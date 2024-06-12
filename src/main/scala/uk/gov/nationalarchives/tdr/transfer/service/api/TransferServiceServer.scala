package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.data.Kleisli
import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.{IpLiteralSyntax, Port}
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.http4s.{HttpRoutes, Request, Response}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import uk.gov.nationalarchives.tdr.transfer.service.Config.Configuration

object TransferServiceServer extends IOApp {
  private val config = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load database migration config${value.prettyPrint()}")
    case Right(value) => value
  }
  private val apiPort: Port = Port.fromInt(config.api.port).getOrElse(port"8080")

  val healthCheckRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "healthcheck" =>
    Ok("Healthy")
  }

  private val app: Kleisli[IO, Request[IO], Response[IO]] = Router(
    "/" -> healthCheckRoute
  ).orNotFound

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
