package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.unsafe.implicits.global
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{doAnswer, mock}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag

class GraphQlApiServiceSpec extends ExternalServicesSpec with Matchers {
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment("authUrl", "realm", 60)

  val mockKeycloakToken: Token = mock[Token]
  val keycloak = mock[KeycloakUtils]

  "'createConsignment'" should "return the consignment id of the created consignment" in {
    doAnswer(() => new BearerAccessToken("token"))
      .when(mockKeycloakToken)
      .bearerAccessToken

    doAnswer(() => Future(new BearerAccessToken("token")))
      .when(keycloak)
      .serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])

    val response = GraphQlApiService.apply().createConsignment(mockKeycloakToken).unsafeRunSync()

    response.consignmentid.get shouldBe UUID.fromString("ae4b7cad-ee83-46bd-b952-80bc8263c6c2")
  }
}
