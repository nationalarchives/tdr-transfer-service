package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import io.circe.syntax.{KeyOps, _}
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.{Header, Headers, Method, Request, Status}
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, userId, validClientToken, validUserToken}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.{LoadController, TransferManagementController}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadCompletion, LoadDetails, LoadError, TransferConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.services.ExternalServicesSpec

class TransferServiceServerSpec extends ExternalServicesSpec with Matchers {
  val transferId = "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"
  val transferRef = "Consignment-Ref"
  private val invalidTokenExpectedResponse = Json.obj(
    "message" := "Invalid token issuer. Expected 'http://localhost:8000/auth/realms/tdr'"
  )

  "'healthcheck' endpoint" should "return 200 if server running" in {
    val getHealthCheck = Request[IO](Method.GET, uri"/healthcheck")
    val response = TransferServiceServer.healthCheckRoute.orNotFound(getHealthCheck).unsafeRunSync()

    response.status shouldBe Status.Ok
    response.as[String].unsafeRunSync() shouldEqual "Healthy"
  }

  "'load/sharepoint/configuration' endpoint" should "return 200 with correct authorisation header" in {
    graphqlOkJson()
    val validToken = validUserToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .configurationRoute
      .orNotFound
      .run(
        Request(method = Method.GET, uri = uri"/load/sharepoint/configuration", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    val transferConfiguration = body.as[TransferConfiguration].toOption.get
    transferConfiguration.maxNumberRecords shouldBe 3000
    transferConfiguration.customMetadataConfiguration.required shouldBe false
    transferConfiguration.metadataPropertyDetails.size shouldBe 7
    transferConfiguration.display.size shouldBe 0
  }

  "'load/sharepoint/configuration' endpoint" should "return 401 response with incorrect authorisation header" in {
    val token = invalidToken
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$token")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .configurationRoute
      .orNotFound
      .run(
        Request(method = Method.GET, uri = uri"/load/sharepoint/configuration", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Unauthorized
    response.as[Json].unsafeRunSync() shouldEqual invalidTokenExpectedResponse
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

    val expectedRecordsDestination = AWSS3LoadDestination("aws-region", "s3BucketNameRecordsArn", "s3BucketNameRecordsName", s"$userId/sharepoint/$transferId/records")
    val expectedMetadataLoadDestination = AWSS3LoadDestination("aws-region", "s3BucketNameMetadataArn", "s3BucketNameMetadataName", s"$userId/sharepoint/$transferId/metadata")

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    val loadDetails = body.as[LoadDetails].toOption.get
    loadDetails.transferId.toString shouldBe transferId
    loadDetails.transferReference shouldBe transferRef
    loadDetails.metadataLoadDestination shouldEqual expectedMetadataLoadDestination
    loadDetails.recordsLoadDestination shouldEqual expectedRecordsDestination
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
    val loadCompletionBody = LoadCompletion(3, 3, Set(LoadError("There was an error"))).asJson

    val validToken = validUserToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .completeLoadRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/load/sharepoint/complete/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e", headers = fakeHeaders).withEntity(loadCompletionBody)
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

  "unknown source system in endpoint" should "return 400 response with incorrect authorisation header" in {
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

  "'processing/sharepoint/upload/completed' endpoint" should "return 200 with correct authorisation header" in {
    val validToken = validClientToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)
    val response = TransferManagementController
      .apply()
      .uploadProcessingRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/processing/sharepoint/upload/completed/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    response.as[String].unsafeRunSync() shouldEqual "\"Upload Processing: Stubbed Response\""
  }

  "'processing/sharepoint/upload/completed' endpoint" should "return 401 response with incorrect authorisation header" in {
    val token = invalidToken
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$token")
    val fakeHeaders = Headers.apply(authHeader)
    val response = TransferManagementController
      .apply()
      .uploadProcessingRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/processing/sharepoint/upload/completed/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Unauthorized
    response.as[Json].unsafeRunSync() shouldEqual invalidTokenExpectedResponse
  }

  "unknown state value in endpoint" should "return 400 response with correct authorisation header" in {
    val validToken = validClientToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)
    val response = TransferManagementController
      .apply()
      .uploadProcessingRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/processing/sharepoint/upload/unknownStateValue/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.BadRequest
  }

  "unknown state value in endpoint" should "return 400 response with incorrect authorisation header" in {
    val token = invalidToken
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$token")
    val fakeHeaders = Headers.apply(authHeader)
    val response = TransferManagementController
      .apply()
      .uploadProcessingRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/processing/sharepoint/upload/unknownStateValue/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.BadRequest
  }
}
