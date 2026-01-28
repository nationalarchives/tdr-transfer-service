package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import io.circe.syntax.{KeyOps, _}
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.{Header, Headers, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchers.any
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import org.typelevel.ci.CIString
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, userId, validUserToken}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.{LoadController, TransferErrorsController}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.StatusValue
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel._
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferErrorModel.TransferErrorsResults
import uk.gov.nationalarchives.tdr.transfer.service.services.ExternalServicesSpec
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors

import java.util.UUID

class TransferServiceServerSpec extends ExternalServicesSpec with Matchers with TableDrivenPropertyChecks {
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

  def generateUri(uriString: String): Uri = {
    Uri.fromString(uriString).fold(e => throw new RuntimeException(e.message), uri => uri)
  }

  val sources: TableFor2[String, Int] = Table(
    ("Source", "Number of metadata property details"),
    (SourceSystemEnum.NetworkDrive.toString.toLowerCase, 0),
    (SourceSystemEnum.HardDrive.toString.toLowerCase, 0),
    (SourceSystemEnum.SharePoint.toString.toLowerCase, 10)
  )

  forAll(sources) { (source, numberPropertyDetails) =>
    val uri = generateUri(s"/load/$source/configuration")

    s"'load/$source/configuration' endpoint" should
      "return 200 with correct authorisation header" in {
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
            Request(method = Method.GET, uri = uri, headers = fakeHeaders)
          )
          .unsafeRunSync()

        response.status shouldBe Status.Ok
        val body = response.as[Json].unsafeRunSync()
        val transferConfiguration = body.as[TransferConfiguration].toOption.get
        transferConfiguration.maxNumberRecords shouldBe 3000
        transferConfiguration.customMetadataConfiguration.required shouldBe false
        transferConfiguration.metadataPropertyDetails.size shouldBe numberPropertyDetails
        transferConfiguration.display.size shouldBe 0
      }

    s"'load/$source/configuration' endpoint" should "return 401 response with incorrect authorisation header" in {
      val token = invalidToken
      val bearer = CIString("Authorization")
      val authHeader = Header.Raw.apply(bearer, s"$token")
      val fakeHeaders = Headers.apply(authHeader)
      val response = LoadController
        .apply()
        .configurationRoute
        .orNotFound
        .run(
          Request(method = Method.GET, uri = uri, headers = fakeHeaders)
        )
        .unsafeRunSync()

      response.status shouldBe Status.Unauthorized
      response.as[Json].unsafeRunSync() shouldEqual invalidTokenExpectedResponse
    }
  }

  forAll(sources) { (source, _) =>
    val uri = generateUri(s"/load/$source/initiate")
    val expectedRecordsDestination = AWSS3LoadDestination("aws-region", "s3BucketNameRecordsArn", "s3BucketNameRecordsName", s"$userId/$source/$transferId/records")
    val expectedMetadataLoadDestination = AWSS3LoadDestination("aws-region", "s3BucketNameMetadataArn", "s3BucketNameMetadataName", s"$userId/$source/$transferId/metadata")

    s"'load/$source/initiate' endpoint" should "return 200 with correct authorisation header" in {
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
          Request(method = Method.POST, uri = uri, headers = fakeHeaders)
        )
        .unsafeRunSync()

      response.status shouldBe Status.Ok
      val body = response.as[Json].unsafeRunSync()
      val loadDetails = body.as[LoadDetails].toOption.get
      loadDetails.transferId.toString shouldBe transferId
      loadDetails.transferReference shouldBe transferRef
      loadDetails.metadataLoadDestination shouldEqual expectedMetadataLoadDestination
      loadDetails.recordsLoadDestination shouldEqual expectedRecordsDestination
    }

    s"'load/$source/initiate' endpoint with optional transfer id argument" should "return 200 with correct authorisation header" in {
      val uriOptionalTransferId = generateUri(s"/load/$source/initiate/?transferId=$transferId")
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
          Request(method = Method.POST, uri = uriOptionalTransferId, headers = fakeHeaders)
        )
        .unsafeRunSync()

      response.status shouldBe Status.Ok
      val body = response.as[Json].unsafeRunSync()
      val loadDetails = body.as[LoadDetails].toOption.get
      loadDetails.transferId.toString shouldBe transferId
      loadDetails.transferReference shouldBe transferRef
      loadDetails.metadataLoadDestination shouldEqual expectedMetadataLoadDestination
      loadDetails.recordsLoadDestination shouldEqual expectedRecordsDestination
    }

    s"'load/$source/initiate' endpoint with optional transfer id argument" should "return 500 response when transfer not in correct upload state" in {
      val uriOptionalTransferId = generateUri(s"/load/$source/initiate/?transferId=${UUID.randomUUID()}")
      graphqlOkJson(uploadStatusValue = StatusValue.Completed.toString)
      val validToken = validUserToken()
      val bearer = CIString("Authorization")
      val authHeader = Header.Raw.apply(bearer, s"$validToken")
      val fakeHeaders = Headers.apply(authHeader)
      val response = LoadController
        .apply()
        .initiateLoadRoute
        .orNotFound
        .run(
          Request(method = Method.POST, uri = uriOptionalTransferId, headers = fakeHeaders)
        )
        .unsafeRunSync()

      response.status shouldBe Status.InternalServerError
    }

    s"'load/$source/initiate' endpoint with optional transfer id argument" should "return 500 response when transfer does not exist" in {
      val uriOptionalTransferId = generateUri(s"/load/$source/initiate/?transferId=${UUID.randomUUID()}")
      graphqlOkJson(consignmentExists = false)
      val validToken = validUserToken()
      val bearer = CIString("Authorization")
      val authHeader = Header.Raw.apply(bearer, s"$validToken")
      val fakeHeaders = Headers.apply(authHeader)
      val response = LoadController
        .apply()
        .initiateLoadRoute
        .orNotFound
        .run(
          Request(method = Method.POST, uri = uriOptionalTransferId, headers = fakeHeaders)
        )
        .unsafeRunSync()

      response.status shouldBe Status.InternalServerError
    }

    s"'load/$source/initiate' endpoint" should "return 401 response with incorrect authorisation header" in {
      val token = invalidToken
      val bearer = CIString("Authorization")
      val authHeader = Header.Raw.apply(bearer, s"$token")
      val fakeHeaders = Headers.apply(authHeader)
      val response = LoadController
        .apply()
        .initiateLoadRoute
        .orNotFound
        .run(
          Request(method = Method.POST, uri = uri, headers = fakeHeaders)
        )
        .unsafeRunSync()

      response.status shouldBe Status.Unauthorized
      response.as[Json].unsafeRunSync() shouldEqual invalidTokenExpectedResponse
    }
  }

  forAll(sources) { (source, _) =>
    {
      val uri = generateUri(s"/load/$source/complete/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")

      s"'load/$source/complete' endpoint" should "return 200 with correct authorisation header" in {
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
            Request(method = Method.POST, uri = uri, headers = fakeHeaders).withEntity(loadCompletionBody)
          )
          .unsafeRunSync()

        response.status shouldBe Status.Ok
        response.as[String].unsafeRunSync() shouldEqual "{\"transferId\":\"6e3b76c4-1745-4467-8ac5-b4dd736e1b3e\",\"success\":false}"
      }

      s"'load/$source/complete' endpoint" should "return 401 response with incorrect authorisation header" in {
        val token = invalidToken
        val bearer = CIString("Authorization")
        val authHeader = Header.Raw.apply(bearer, s"$token")
        val fakeHeaders = Headers.apply(authHeader)
        val response = LoadController
          .apply()
          .completeLoadRoute
          .orNotFound
          .run(
            Request(method = Method.POST, uri = uri, headers = fakeHeaders)
          )
          .unsafeRunSync()

        response.status shouldBe Status.Unauthorized
        response.as[Json].unsafeRunSync() shouldEqual invalidTokenExpectedResponse
      }
    }
  }

  s"'errors/load/' endpoint" should "return 200 with correct authorisation header" in {
    graphqlOkJson(uploadStatusValue = StatusValue.Completed.toString)
    val validToken = validUserToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)

    val jsonResponse = Json.obj(
      "consignmentId" := s"$transferId",
      "errorCode" := "AGGREGATE_PROCESSING.CLIENT_DATA_LOAD.FAILURE",
      "errorMessage" := s"Client data load errors for consignment: $transferId"
    )

    val mockedTransferErrors = mock[TransferErrors]
    when(mockedTransferErrors.getTransferErrors(any[Token](), any[Option[UUID]]()))
      .thenReturn(
        IO.pure(
          TransferErrorsResults(
            uploadCompleted = true,
            errors = List(jsonResponse),
            transferId = UUID.fromString(transferId)
          )
        )
      )

    val controller = new TransferErrorsController(mockedTransferErrors)

    val response = controller.getErrorsRoute.orNotFound
      .run(
        Request(method = Method.GET, uri = generateUri(s"/errors/load/$transferId"), headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    val expectedJson = Json.obj(
      "uploadCompleted" -> Json.fromBoolean(true),
      "errors" -> Json.arr(jsonResponse),
      "transferId" -> Json.fromString(transferId)
    )
    body shouldEqual expectedJson
  }

  s"'errors/load/' endpoint" should "return 401 response when an exception is thrown" in {
    graphqlOkJson(uploadStatusValue = StatusValue.Completed.toString)
    val validToken = validUserToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)

    val response = TransferErrorsController
      .apply()
      .getErrorsRoute
      .orNotFound
      .run(
        Request(method = Method.GET, uri = generateUri(s"/errors/load/$transferId"), headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Unauthorized
    val body = response.as[Json].unsafeRunSync()
    body.isNull shouldBe false
  }

  s"'errors/load/' endpoint" should "return 401 response with incorrect authorisation header" in {
    val token = invalidToken
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$token")
    val fakeHeaders = Headers.apply(authHeader)

    val response = TransferErrorsController
      .apply()
      .getErrorsRoute
      .orNotFound
      .run(
        Request(method = Method.GET, uri = generateUri(s"/errors/load/$transferId"), headers = fakeHeaders)
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
}
