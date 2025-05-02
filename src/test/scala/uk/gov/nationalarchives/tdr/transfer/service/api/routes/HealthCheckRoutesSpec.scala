package uk.gov.nationalarchives.tdr.transfer.service.api.routes

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Method, Request, Status}
import org.scalatest.matchers.should.Matchers
import uk.gov.nationalarchives.tdr.transfer.service.api.TransferServiceServer
import uk.gov.nationalarchives.tdr.transfer.service.services.ExternalServicesSpec

class HealthCheckRoutesSpec extends ExternalServicesSpec with Matchers {
  "'healthcheck' endpoint" should "return 200 if server running" in {
    val getHealthCheck = Request[IO](Method.GET, uri"/healthcheck")
    val response = TransferServiceServer.healthCheckRoute.orNotFound(getHealthCheck).unsafeRunSync()

    response.status shouldBe Status.Ok
    response.as[String].unsafeRunSync() shouldEqual "Healthy"
  }
}
