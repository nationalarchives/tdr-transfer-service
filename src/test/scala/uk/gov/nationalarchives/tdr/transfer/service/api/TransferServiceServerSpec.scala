package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.generic.auto.exportEncoder
import io.circe.syntax.KeyOps
import org.http4s.circe.jsonDecoder
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Header, Headers, Method, Request, Status}
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, userId, validUserToken}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.LoadController
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.services.ExternalServicesSpec

class TransferServiceServerSpec extends ExternalServicesSpec with Matchers {

  val transferId = "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"
  private val invalidTokenExpectedResponse = Json.obj(
    "message" := "Invalid token issuer. Expected 'http://localhost:8000/auth/realms/tdr'"
  )

  "'healthcheck' endpoint" should "return 200 if server running" in {
    val getHealthCheck = Request[IO](Method.GET, uri"/healthcheck")
    val response = TransferServiceServer.healthCheckRoute.orNotFound(getHealthCheck).unsafeRunSync()

    response.status shouldBe Status.Ok
    response.as[String].unsafeRunSync() shouldEqual "Healthy"
  }

  "'load/sharepoint/initiate' endpoint" should "return 200 with correct authorisation header" in {
    graphqlOkJson()
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
      "bucketKeyPrefix" := s"$userId/$transferId"
    )

    val metadataLoadDestination = Json.obj(
      "bucketName" := "s3BucketNameMetadata",
      "bucketKeyPrefix" := s"$transferId/dataload"
    )

    val metadataProperties: List[MetadataPropertyDetails] = List()

    val expectedResponse = Json.obj(
      "transferId" := transferId,
      "recordsLoadDestination" := recordsDestination,
      "metadataLoadDestination" := metadataLoadDestination,
      "metadataProperties" := metadataProperties
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
    response.as[Json].unsafeRunSync() shouldEqual invalidTokenExpectedResponse
  }

  "'load/sharepoint/complete' endpoint" should "return 200 with correct authorisation header" in {
    val validToken = validUserToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .completeLoadRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/load/sharepoint/complete/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    response.as[String].unsafeRunSync() shouldEqual "\"Data Load Processor: Stubbed Response\""
  }

  "'load/sharepoint/complete' endpoint" should "return 401 response with incorrect authorisation header" in {
    val token = invalidToken
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$token")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .completeLoadRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/load/sharepoint/complete/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Unauthorized
    response.as[Json].unsafeRunSync() shouldEqual invalidTokenExpectedResponse
  }
}
