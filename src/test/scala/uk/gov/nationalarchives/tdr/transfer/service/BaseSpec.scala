package uk.gov.nationalarchives.tdr.transfer.service

import cats.effect.IO
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.{Keycloak, KeycloakBuilder}
import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.TdrKeycloakDeployment
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{Header, MetadataPropertyDetails, TransferConfiguration}

import scala.concurrent.ExecutionContextExecutor
import scala.io.Source.fromResource

trait BaseSpec extends AnyFlatSpec with MockitoSugar with Matchers with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment("authUrl", "realm", 60)

  val expectedS3PutRequestHeaders: Set[Header] = Set(
    Header("ACL", "acl-header-value"),
    Header("If-None-Match", "if-none-match-header-value")
  )

  val expectedMetadataPropertyDetails: Set[MetadataPropertyDetails] = Set(
    MetadataPropertyDetails("SHA256ClientSideChecksum", required = true),
    MetadataPropertyDetails("transferId", required = true),
    MetadataPropertyDetails("matchId", required = true),
    MetadataPropertyDetails("source", required = true),
    MetadataPropertyDetails("userId", required = true),
    MetadataPropertyDetails("Modified", required = true),
    MetadataPropertyDetails("Length", required = true),
    MetadataPropertyDetails("FileRef", required = true),
    MetadataPropertyDetails("userId", required = true),
    MetadataPropertyDetails("FileLeafRef", required = true),
    MetadataPropertyDetails("File_x0020_Type", required = true),
    MetadataPropertyDetails("TimeCreated", required = true)
  )

  val expectedTransferConfiguration: TransferConfiguration =
    TransferConfiguration(3000, 2000, 5000, Set(), expectedMetadataPropertyDetails, display = Set(), s3PutRequestHeaders = expectedS3PutRequestHeaders)

  val keycloakUserId = "b2657adf-6e93-424f-b0f1-aadd26762a96"
  val authPath = "/auth/realms/tdr/protocol/openid-connect/token"
  val keycloakGetRealmPath = "/auth/admin/realms/tdr"
  val keycloakGetUserPath: String = s"/auth/admin/realms/tdr/users/$keycloakUserId"

  val keycloakAdminClient: Keycloak = KeycloakBuilder
    .builder()
    .serverUrl("http://localhost:9002/auth")
    .realm("tdr")
    .clientId("auth-client-id")
    .clientSecret("auth-client-secret")
    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
    .build()

  val wiremockAuthServer = new WireMockServer(9002)

  def keycloakCreateAdminClient: Keycloak = keycloakAdminClient

  def authOk: StubMapping = wiremockAuthServer.stubFor(
    post(urlEqualTo(authPath))
      .willReturn(okJson(fromResource(s"json/access_token.json").mkString))
  )

  def keycloakGetUser: StubMapping = wiremockAuthServer.stubFor(
    WireMock
      .get(urlEqualTo(keycloakGetUserPath))
      .willReturn(okJson(fromResource(s"json/get_keycloak_user.json").mkString))
  )

  override def beforeAll(): Unit = {
    wiremockAuthServer.start()
  }

  override def afterAll(): Unit = {
    wiremockAuthServer.stop()
  }

  override def afterEach(): Unit = {
    wiremockAuthServer.resetAll()
  }
}
