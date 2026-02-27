package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.data.Kleisli
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits.toSemigroupKOps
import com.comcast.ip4s.{IpLiteralSyntax, Port}
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{CSRF, HSTS, Logger, Throttle}
import org.http4s.{HttpApp, HttpRoutes, Uri}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.apispec.openapi.Info
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import uk.gov.nationalarchives.tdr.keycloak.TdrKeycloakDeployment
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.{LoadController, TransferErrorsController}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Strict-Transport-Security`
import org.http4s.server.middleware.{CSRF, HSTS}
import uk.gov.nationalarchives.tdr.transfer.service.api.middleware.SecurityHeaders

import scala.concurrent.duration.DurationInt

object TransferServiceServer extends IOApp {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  private val appConfig = ApplicationConfig.appConfig
  private val authUrl = appConfig.auth.url
  private val realm = appConfig.auth.realm
  private val domain = appConfig.transferServiceApi.domain

  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val keycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(s"$authUrl", realm, 8080)

  private val apiPort: Port = Port.fromInt(appConfig.transferServiceApi.port).getOrElse(port"8080")

  private val infoTitle = "TDR Transfer Service API"
  private val infoVersion = "0.0.3"
  private val infoDescription = Some("APIs to allow client services to transfer records to TDR")

  private val openApiInfo: Info = Info(infoTitle, infoVersion, description = infoDescription)
  private val loadController = LoadController()
  private val transferErrorsController = TransferErrorsController()

  private val documentationEndpoints =
    SwaggerInterpreter().fromEndpoints[IO](loadController.endpoints ++ transferErrorsController.endpoints, openApiInfo)

  val healthCheckRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "healthcheck" =>
    Ok("Healthy")
  }

  val allRoutes = if (appConfig.featureAccessBlocks.blockApiDocumentation) {
    loadController.routes <+> transferErrorsController.routes <+> healthCheckRoute
  } else Http4sServerInterpreter[IO]().toRoutes(documentationEndpoints) <+> loadController.routes <+> transferErrorsController.routes <+> healthCheckRoute

  private def throttleService(service: HttpApp[IO]): IO[HttpApp[IO]] = Throttle.httpApp[IO](
    amount = appConfig.transferServiceApi.throttleAmount,
    per = appConfig.transferServiceApi.throttlePerMs.milliseconds
  )(service)

  private val token = CSRF.generateSigningKey[IO]()
  val a: Kleisli[IO, Request[IO], Response[IO]] = allRoutes.orNotFound

  private val csrfService: IO[Kleisli[IO, Request[IO], Response[IO]]] = token.map { key =>
    val cookieName: String = "csrf-token"
    val csrfBuilder: CSRF.CSRFBuilder[IO, IO] = CSRF[IO, IO](key, request => CSRF.defaultOriginCheck[IO](request, domain, Uri.Scheme.http, None))
    csrfBuilder
      .withCookieName(cookieName)
      .withCookieDomain(Some(domain))
      .withCookiePath(Some("/"))
      .build
      .validate()
      .apply(SecurityHeaders.apply(allRoutes).orNotFound)
  }

  private val transferServiceServer = for {
    httpApp <- Resource.eval(csrfService)
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
