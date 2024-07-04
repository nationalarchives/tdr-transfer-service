package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.syntax.KeyOps
import org.http4s.circe.jsonDecoder
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Header, Headers, Method, Request, Status}
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, userId, validUserToken}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.LoadController
import uk.gov.nationalarchives.tdr.transfer.service.services.ExternalServicesSpec

class TransferServiceServerSpec extends ExternalServicesSpec with Matchers {

  val consignmentId = "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"

  "'healthcheck' endpoint" should "return 200 if server running" in {
    val getHealthCheck = Request[IO](Method.GET, uri"/healthcheck")
    val response = TransferServiceServer.healthCheckRoute.orNotFound(getHealthCheck).unsafeRunSync()
    response.status shouldBe Status.Ok

    response.status shouldBe Status.Ok
    response.as[String].unsafeRunSync() shouldEqual "Healthy"
  }

  "'load/sharepoint/initiate' endpoint" should "return 200 with correct authorisation header" in {
    graphqlOkJson
    val validToken = validUserToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .initiateLoadRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/load/sharepoint/initiate", headers = fakeHeaders)
      )
      .unsafeRunSync()

    val recordsDestination = Json.obj(
      "bucketName" := "s3BucketNameRecords",
      "bucketKey" := s"$userId/$consignmentId"
    )

    val metadataLoadDestination = Json.obj(
      "bucketName" := "s3BucketNameMetadata",
      "bucketKey" := s"$consignmentId/dataload/data-load-metadata.csv"
    )

    val expectedResponse = Json.obj(
      "consignmentId" := consignmentId,
      "recordsLoadDestination" := recordsDestination,
      "metadataLoadDestination" := metadataLoadDestination
    )

    response.status shouldBe Status.Ok
    response.as[Json].unsafeRunSync() shouldEqual expectedResponse
  }

  "'load/sharepoint/initiate' endpoint" should "return 401 response with incorrect authorisation header" in {
    val token = invalidToken
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$token")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .initiateLoadRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/load/sharepoint/initiate", headers = fakeHeaders)
      )
      .unsafeRunSync()

    val expectedResponse = Json.obj(
      "message" := "Invalid token issuer. Expected 'http://localhost:8000/auth/realms/tdr'"
    )

    response.status shouldBe Status.Unauthorized
    response.as[Json].unsafeRunSync() shouldEqual expectedResponse
  }
}
