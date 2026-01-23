package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits.toSemigroupKOps
import com.comcast.ip4s.{IpLiteralSyntax, Port}
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{Logger, Throttle}
import org.http4s.{HttpApp, HttpRoutes}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.apispec.openapi.Info
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import uk.gov.nationalarchives.tdr.keycloak.TdrKeycloakDeployment
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.{TransferErrorsController, LoadController}

import scala.concurrent.duration.DurationInt

object TransferServiceServer extends IOApp {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  private val appConfig = ApplicationConfig.appConfig
  private val authUrl = appConfig.auth.url
  private val realm = appConfig.auth.realm

  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val keycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(s"$authUrl", realm, 8080)

  private val apiPort: Port = Port.fromInt(appConfig.transferServiceApi.port).getOrElse(port"8080")

  private val infoTitle = "TDR Transfer Service API"
  private val infoVersion = "0.0.3"
  private val infoDescription = Some("APIs to allow client services to transfer records to TDR")

  private val openApiInfo: Info = Info(infoTitle, infoVersion, description = infoDescription)
  private val loadController = LoadController()
  private val errorController = TransferErrorsController()

  private val documentationEndpoints =
    SwaggerInterpreter().fromEndpoints[IO](loadController.endpoints ++ errorController.endpoints, openApiInfo)

  val healthCheckRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "healthcheck" =>
    Ok("Healthy")
  }

  private val allRoutes =
    Http4sServerInterpreter[IO]().toRoutes(documentationEndpoints) <+> loadController.routes <+> errorController.routes <+> healthCheckRoute

  private def throttleService(service: HttpApp[IO]): IO[HttpApp[IO]] = Throttle.httpApp[IO](
    amount = appConfig.transferServiceApi.throttleAmount,
    per = appConfig.transferServiceApi.throttlePerMs.milliseconds
  )(service)

  private val transferServiceServer = for {
    httpApp <- Resource.eval(throttleService(allRoutes.orNotFound))
    finalApp = Logger.httpApp(logHeaders = true, logBody = false)(httpApp)
    server <- EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(apiPort)
      .withHttpApp(finalApp)
      .build
  } yield server

  override def run(args: List[String]): IO[ExitCode] = {
    transferServiceServer.use(_ => IO.never).as(ExitCode.Success)
  }
}
