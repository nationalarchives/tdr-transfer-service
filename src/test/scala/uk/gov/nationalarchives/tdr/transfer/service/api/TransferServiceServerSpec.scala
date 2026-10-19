package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Port
import io.circe.Json
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.{Header, Headers, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchers.any
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3}
import org.typelevel.ci.CIString
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.{SeriesType, UploadType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.CompletedValue
import uk.gov.nationalarchives.tdr.schema.generated.ExcludedFilenames
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, userId, validUserToken}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.Tls
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation.AuthorisationInput
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.{LoadController, TransferErrorsController}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.AuthenticationError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel._
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferErrorResultsModel.TransferErrorsResults
import uk.gov.nationalarchives.tdr.transfer.service.services.ExternalServicesSpec
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.{DataLoadConfiguration, DataLoadInitiation, DataLoadProcessor}
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors.TransferErrorsInput

import java.io.IOException
import java.net.URI
import java.nio.file.{Files, Path}
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.UUID
import javax.net.ssl.{HttpsURLConnection, SSLContext, TrustManagerFactory}
import scala.concurrent.duration._
import scala.sys.process._

class TransferServiceServerSpec extends ExternalServicesSpec with Matchers with TableDrivenPropertyChecks {
  val transferId = "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"
  val transferRef = "Consignment-Ref"
  private val invalidTokenExpectedResponse = Json.obj(
    "message" := "Invalid token issuer. Expected 'http://localhost:8000/auth/realms/tdr'"
  )
  private val sharePointUploadFacExpectedResponse = Json.obj(
    "message" := s"User $userId does not have permission to upload from SharePoint"
  )

  "'healthcheck' endpoint" should "return 200 if server running" in {
    val getHealthCheck = Request[IO](Method.GET, uri"/healthcheck")
    val response = TransferServiceServer.healthCheckRoute.orNotFound(getHealthCheck).unsafeRunSync()

    response.status shouldBe Status.Ok
    response.as[String].unsafeRunSync() shouldEqual "Healthy"
  }

  private def tlsFiles: Resource[IO, Tls] =
    Resource
      .make(IO.blocking(Files.createTempDirectory("transfer-service-tls")))(directory =>
        IO.blocking {
          Files.deleteIfExists(directory.resolve("server.key"))
          Files.deleteIfExists(directory.resolve("server.crt"))
          Files.delete(directory)
        }
      )
      .evalMap { directory =>
        IO.blocking {
          val key = directory.resolve("server.key")
          val certificate = directory.resolve("server.crt")
          val output = new StringBuilder
          val exitCode = Process(
            Seq(
              "openssl",
              "req",
              "-x509",
              "-newkey",
              "rsa:2048",
              "-sha256",
              "-noenc",
              "-days",
              "1",
              "-keyout",
              key.toString,
              "-out",
              certificate.toString,
              "-subj",
              "/CN=localhost",
              "-addext",
              "subjectAltName=DNS:localhost,IP:127.0.0.1"
            )
          ).!(ProcessLogger(line => output.append(line), line => output.append(line)))
          require(exitCode == 0, s"Could not generate test TLS certificate: $output")
          Tls(certificate.toString, key.toString)
        }
      }

  "TLS server" should "serve HTTPS with the configured certificate and reject plaintext HTTP" in {
    tlsFiles
      .flatMap(tls => TransferServiceServer.serverResource(TransferServiceServer.healthCheckRoute.orNotFound, Port.fromInt(0).get, tls).map(tls -> _))
      .use { case (tls, server) =>
        IO.blocking {
          val trustStore = KeyStore.getInstance("PKCS12")
          trustStore.load(null, null)
          val input = Files.newInputStream(Path.of(tls.certificatePath))
          try trustStore.setCertificateEntry("server", CertificateFactory.getInstance("X.509").generateCertificate(input))
          finally input.close()
          val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
          trustManagerFactory.init(trustStore)
          val context = SSLContext.getInstance("TLS")
          context.init(null, trustManagerFactory.getTrustManagers, null)
          val https = URI.create(s"https://localhost:${server.address.getPort}/healthcheck").toURL.openConnection().asInstanceOf[HttpsURLConnection]
          https.setSSLSocketFactory(context.getSocketFactory)
          https.setConnectTimeout(3000)
          https.setReadTimeout(3000)
          try {
            https.getResponseCode shouldBe 200
            val body = https.getInputStream
            try new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) shouldBe "Healthy"
            finally body.close()
          } finally https.disconnect()

          val http = URI.create(s"http://localhost:${server.address.getPort}/healthcheck").toURL.openConnection()
          http.setConnectTimeout(3000)
          http.setReadTimeout(3000)
          intercept[IOException](http.getInputStream)
        }
      }
      .timeout(15.seconds)
      .unsafeRunSync()
  }

  it should "fail startup when the certificate is missing" in {
    tlsFiles
      .use { tls =>
        TransferServiceServer
          .serverResource(TransferServiceServer.healthCheckRoute.orNotFound, Port.fromInt(0).get, tls.copy(certificatePath = tls.certificatePath + ".missing"))
          .use(_ => IO.unit)
          .attempt
          .map(result => result.left.toOption.get shouldBe a[java.nio.file.NoSuchFileException])
      }
      .unsafeRunSync()
  }

  it should "fail startup when the private key is invalid" in {
    tlsFiles
      .use { tls =>
        IO.blocking(Files.writeString(Path.of(tls.privateKeyPath), "invalid private key")) *>
          TransferServiceServer
            .serverResource(TransferServiceServer.healthCheckRoute.orNotFound, Port.fromInt(0).get, tls)
            .use(_ => IO.unit)
            .attempt
            .map(result => result.left.toOption.get shouldBe an[IllegalArgumentException])
      }
      .unsafeRunSync()
  }

  "'docs' endpoint" should "return 404 if the feature is blocked" in {
    val getDocs = Request[IO](Method.GET, uri"/docs/")
    val response = TransferServiceServer.allRoutes.orNotFound(getDocs).unsafeRunSync()
    response.status shouldBe Status.NotFound
  }

  def generateUri(uriString: String): Uri = {
    Uri.fromString(uriString).fold(e => throw new RuntimeException(e.message), uri => uri)
  }

  val sources: TableFor3[String, Int, StatusCode] = Table(
    ("Source", "Number of metadata property details", "sharepoint feature access status code"),
    (SourceSystemEnum.NetworkDrive.toString.toLowerCase, 0, StatusCode.Ok),
    (SourceSystemEnum.HardDrive.toString.toLowerCase, 0, StatusCode.Ok),
    (SourceSystemEnum.SharePoint.toString.toLowerCase, 11, StatusCode.Forbidden)
  )

  private def checkValidResponseHeaders(headers: Headers) = {
    headers.headers.size shouldBe 7
    headers.get(CIString("X-Content-Type-Options")).get.head.value shouldBe "nosniff"
    headers.get(CIString("Strict-Transport-Security")).get.head.value shouldBe "max-age=31536000; includeSubDomains"
    headers.get(CIString("X-Frame-Options")).get.head.value shouldBe "DENY"
    headers.get(CIString("Referrer-Policy")).get.head.value shouldBe "origin"
    headers.get(CIString("Content-Security-Policy")).get.head.value shouldBe
      "child-src 'self'; default-src 'self'; base-uri 'none'; script-src 'strict-dynamic'; connect-src 'self'; object-src 'none'"
  }

  forAll(sources) { (source, numberPropertyDetails, sharePointFacStatus) =>
    val uri = generateUri(s"/load/$source/configuration")

    s"'load/$source/configuration' endpoint" should
      "return 200 with correct authorisation header" in {
        graphqlOkJson(UUID.fromString(transferId))
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
        checkValidResponseHeaders(response.headers)
        val body = response.as[Json].unsafeRunSync()
        val transferConfiguration = body.as[TransferConfiguration].toOption.get
        transferConfiguration.maxNumberRecords shouldBe 3000
        transferConfiguration.customMetadataConfiguration.required shouldBe false
        transferConfiguration.metadataPropertyDetails.size shouldBe numberPropertyDetails
        transferConfiguration.disallowedFileExtensions.size shouldBe 0
        transferConfiguration.disallowedFileNames.size shouldBe ExcludedFilenames.all.size
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

    s"'load/$source/configuration' endpoint" should
      s"return ${sharePointFacStatus.code} response when user access no access to sharepoint upload feature" in {
        val token = validUserToken(connectorForSharePointUser = "false")
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

        response.status.code shouldBe sharePointFacStatus.code
        if (response.status.code != 200) {
          response.as[Json].unsafeRunSync() shouldEqual sharePointUploadFacExpectedResponse
        }
      }
  }

  forAll(sources) { (source, _, sharePointFacStatus) =>
    val uri = generateUri(s"/load/$source/initiate")
    val expectedRecordsDestination = AWSS3LoadDestination("aws-region", "s3BucketNameRecordsArn", "s3BucketNameRecordsName", s"$userId/$source/$transferId/records")
    val expectedMetadataLoadDestination = AWSS3LoadDestination("aws-region", "s3BucketNameMetadataArn", "s3BucketNameMetadataName", s"$userId/$source/$transferId/metadata")

    s"'load/$source/initiate' endpoint" should "return 200 with correct authorisation header" in {
      graphqlOkJson(UUID.fromString(transferId))
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
      checkValidResponseHeaders(response.headers)
      val body = response.as[Json].unsafeRunSync()
      val loadDetails = body.as[LoadDetails].toOption.get
      loadDetails.transferId.toString shouldBe transferId
      loadDetails.transferReference shouldBe transferRef
      loadDetails.metadataLoadDestination shouldEqual expectedMetadataLoadDestination
      loadDetails.recordsLoadDestination shouldEqual expectedRecordsDestination
    }

    s"'load/$source/initiate' endpoint" should s"return ${sharePointFacStatus.code} response when user access no access to sharepoint upload feature" in {
      graphqlOkJson(UUID.fromString(transferId))
      val validToken = validUserToken(connectorForSharePointUser = "false")
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

      response.status.code shouldBe sharePointFacStatus.code

      if (response.status.code != 200) {
        response.as[Json].unsafeRunSync() shouldEqual sharePointUploadFacExpectedResponse
      }
    }

    s"'load/$source/initiate' endpoint with optional transfer id argument" should "return 200 with correct authorisation header" in {
      val uriOptionalTransferId = generateUri(s"/load/$source/initiate/?transferId=$transferId")
      graphqlOkJson(UUID.fromString(transferId))
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
      checkValidResponseHeaders(response.headers)
      val body = response.as[Json].unsafeRunSync()
      val loadDetails = body.as[LoadDetails].toOption.get
      loadDetails.transferId.toString shouldBe transferId
      loadDetails.transferReference shouldBe transferRef
      loadDetails.metadataLoadDestination shouldEqual expectedMetadataLoadDestination
      loadDetails.recordsLoadDestination shouldEqual expectedRecordsDestination
    }

    s"'load/$source/initiate' endpoint with optional transfer id argument" should s"return ${sharePointFacStatus.code} when user does not have access to sharepoint upload feature" in {
      val uriOptionalTransferId = generateUri(s"/load/$source/initiate/?transferId=$transferId")
      graphqlOkJson(UUID.fromString(transferId))
      val validToken = validUserToken(connectorForSharePointUser = "false")
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

      response.status.code shouldBe sharePointFacStatus.code

      if (response.status.code != 200) {
        response.as[Json].unsafeRunSync() shouldEqual sharePointUploadFacExpectedResponse
      }
    }

    s"'load/$source/initiate' endpoint with optional transfer id argument" should "return 403 response when transfer not in correct upload state" in {
      val uriOptionalTransferId = generateUri(s"/load/$source/initiate/?transferId=$transferId")
      graphqlOkJson(UUID.fromString(transferId), overrideStatusType = SeriesType, uploadStatusValue = CompletedValue.value, addUploadStatus = true)
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

      response.status shouldBe Status.Forbidden
      val error = response.as[String].unsafeRunSync()
      error.contains(s"Existing consignment state incorrect for upload: $transferId")
    }

    s"'load/$source/initiate' endpoint with optional transfer id argument" should "return 500 response when transfer does not exist" in {
      val uriOptionalTransferId = generateUri(s"/load/$source/initiate/?transferId=${UUID.randomUUID()}")
      graphqlOkJson(UUID.fromString(transferId), consignmentExists = false)
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

    s"'load/$source/initiate' endpoint with optional transfer id argument" should "return 401 response when user does not have access to the transfer" in {
      val uriOptionalTransferId = generateUri(s"/load/$source/initiate/?transferId=$transferId")
      graphqlOkJson(UUID.fromString(transferId))
      val mockAuthorisation = mock[Authorisation]
      val validToken = validUserToken()
      val bearer = CIString("Authorization")
      val authHeader = Header.Raw.apply(bearer, s"$validToken")
      val fakeHeaders = Headers.apply(authHeader)

      when(mockAuthorisation.endpointAuthorisation(any[AuthorisationInput]))
        .thenReturn(IO.raiseError(AuthenticationError("Authentication error message")))

      val response = new LoadController(DataLoadConfiguration(), DataLoadInitiation(), DataLoadProcessor(), mockAuthorisation).initiateLoadRoute.orNotFound
        .run(
          Request(method = Method.POST, uri = uriOptionalTransferId, headers = fakeHeaders)
        )
        .unsafeRunSync()

      val expectedErrorJson = Json.obj(
        "message" := "Authentication error message"
      )
      response.status shouldBe Status.Unauthorized
      response.as[Json].unsafeRunSync() shouldEqual expectedErrorJson
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

    s"'load/$source/initiate' endpoint" should "return 403 response when a user has too many transfers without a series assigned" in {
      graphqlOkJson(UUID.fromString(transferId), overrideStatusType = UploadType)
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

      response.status shouldBe Status.Forbidden
      val error = response.as[String].unsafeRunSync()
      error.contains(s"User $userId has too many consignments without series assigned")
    }
  }

  forAll(sources) { (source, _, sharePointFacStatus) =>
    {
      val uri = generateUri(s"/load/$source/complete/6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")

      s"'load/$source/complete' endpoint" should "return 200 with correct authorisation header" in {
        graphqlOkJson(UUID.fromString(transferId))
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
        checkValidResponseHeaders(response.headers)
        response.as[String].unsafeRunSync() shouldEqual "{\"transferId\":\"6e3b76c4-1745-4467-8ac5-b4dd736e1b3e\",\"success\":false}"
      }

      s"'load/$source/complete' endpoint" should s"return ${sharePointFacStatus.code} response when user access no access to sharepoint upload feature" in {
        graphqlOkJson(UUID.fromString(transferId))
        val loadCompletionBody = LoadCompletion(3, 3, Set(LoadError("There was an error"))).asJson
        val validToken = validUserToken(connectorForSharePointUser = "false")
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

        response.status.code shouldBe sharePointFacStatus.code

        if (response.status.code != 200) {
          response.as[Json].unsafeRunSync() shouldEqual sharePointUploadFacExpectedResponse
        }
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
    graphqlOkJson(UUID.fromString(transferId), uploadStatusValue = CompletedValue.value)
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
    when(mockedTransferErrors.result(any[TransferErrorsInput]()))
      .thenReturn(
        IO.pure(
          Right(
            TransferErrorsResults(
              uploadCompleted = true,
              errors = List(jsonResponse),
              transferId = UUID.fromString(transferId)
            )
          )
        )
      )

    val controller = new TransferErrorsController(mockedTransferErrors, Authorisation())

    val response = controller.getErrorsRoute.orNotFound
      .run(
        Request(method = Method.GET, uri = generateUri(s"/errors/load/$transferId"), headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    checkValidResponseHeaders(response.headers)
    val body = response.as[Json].unsafeRunSync()
    val expectedJson = Json.obj(
      "uploadCompleted" -> Json.fromBoolean(true),
      "errors" -> Json.arr(jsonResponse),
      "transferId" -> Json.fromString(transferId)
    )
    body shouldEqual expectedJson
  }

  s"'errors/load/' endpoint" should "return 500 response when a user is authenticated but an exception is thrown" in {
    graphqlOkJson(UUID.fromString(transferId), uploadStatusValue = CompletedValue.value)
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

    response.status shouldBe Status.InternalServerError
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
