package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.data.{Kleisli, OptionT}
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import com.comcast.ip4s.{IpLiteralSyntax, Port}
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.http4s.{HttpRoutes, Request, Response}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import sttp.apispec.openapi.Info
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.Config.Configuration
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.LoadController

object TransferServiceServer extends IOApp {
  private val config = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load database migration config${value.prettyPrint()}")
    case Right(value) => value
  }

  private val apiPort: Port = Port.fromInt(config.api.port).getOrElse(port"8080")

  private val infoTitle = "TDR Transfer Service API"
  private val infoVersion = "0.0.1"
  private val infoDescription = Some("APIs to allow client services to transfer records to TDR")

  private val openApiInfo: Info = Info(infoTitle, infoVersion, description = infoDescription)
  private val loadController = LoadController.apply()

  private val documentationEndpoints =
    SwaggerInterpreter().fromEndpoints[IO](loadController.endpoints, openApiInfo)

  val healthCheckRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "healthcheck" =>
    Ok("Healthy")
  }

  private val allRoutes =
    Http4sServerInterpreter[IO]().toRoutes(documentationEndpoints) <+> loadController.routes <+> healthCheckRoute

  private val app: Kleisli[IO, Request[IO], Response[IO]] = allRoutes.orNotFound

  private val finalApp = Logger.httpApp(true, true)(app)

  private val transferServiceServer = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(apiPort)
    .withHttpApp(finalApp)
    .build

  override def run(args: List[String]): IO[ExitCode] = {
    transferServiceServer.use(_ => IO.never).as(ExitCode.Success)
  }
}
