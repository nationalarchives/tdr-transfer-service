package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import io.circe.syntax.KeyOps
import org.http4s.circe.jsonDecoder
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Header, Headers, Method, Request, Status}
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, userId, validUserToken}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.LoadController
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
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

    val expectedRecordsDestination = AWSS3LoadDestination("s3BucketNameRecords", s"$userId/sharepoint/$transferId/records")
    val expectedMetadataLoadDestination = AWSS3LoadDestination("s3BucketNameMetadata", s"$userId/sharepoint/$transferId/metadata")

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    val loadDetails = body.as[LoadDetails].toOption.get
    loadDetails.transferId.toString shouldBe transferId
    loadDetails.metadataLoadDestination shouldEqual expectedMetadataLoadDestination
    loadDetails.recordsLoadDestination shouldEqual expectedRecordsDestination
    loadDetails.transferConfiguration.maxNumberRecords shouldBe 3000
    loadDetails.transferConfiguration.customMetadataConfiguration.required shouldBe false
    loadDetails.transferConfiguration.metadataPropertyDetails.size shouldBe 4
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

  "unknown source system in endpoint" should "return 400 response with correct authorisation header" in {
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
        Request(method = Method.POST, uri = uri"/load/unknown/initiate", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.BadRequest
  }

  "unknown source system in endpoint endpoint" should "return 400 response with incorrect authorisation header" in {
    val token = invalidToken
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$token")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .initiateLoadRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/load/unknown/initiate", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.BadRequest
  }
}
