package uk.gov.nationalarchives.tdr.transfer.service

import cats.data.Kleisli
import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.IpLiteralSyntax
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.Logger

object TransferServiceServer extends IOApp {

  val healthCheckRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "healthcheck" => Ok("Healthy")
  }

  private val app: Kleisli[IO, Request[IO], Response[IO]] = Router(
    "/" -> healthCheckRoute
  ).orNotFound

  private val finalApp = Logger.httpApp(true, true)(app)

  private val transferServiceServer = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(finalApp)
    .build

  override def run(args: List[String]): IO[ExitCode] = {
    transferServiceServer.use(_ => IO.never).as(ExitCode.Success)
  }
}
