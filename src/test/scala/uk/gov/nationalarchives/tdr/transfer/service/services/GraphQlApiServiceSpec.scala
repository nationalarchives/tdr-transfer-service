package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.WireMockServer
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.mockito.MockitoSugar.{doAnswer, mock}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.Configuration
import graphql.codegen.AddConsignment.addConsignment.AddConsignment
import graphql.codegen.AddConsignment.{addConsignment => ac}
import sangria.ast.Document

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag
import pureconfig.generic.auto._
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

class GraphQlApiServiceSpec extends AnyFlatSpec with MockitoSugar with Matchers with EitherValues {
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment("authUrl", "realm", 60)
  implicit val appConfig: Configuration = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load database migration config${value.prettyPrint()}")
    case Right(value) => value
  }

  val mockKeycloakToken: Token = mock[Token]
  val keycloak: KeycloakUtils = mock[KeycloakUtils]
  val addConsignmentClient = mock[GraphQLClient[ac.Data, ac.Variables]]
  val consignmentId = "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"

  "'addConsignment'" should "return the consignment id of the created consignment" in {
    val addConsignmentData = AddConsignment(Some(UUID.fromString(consignmentId)), None)

    doAnswer(() => new BearerAccessToken("token"))
      .when(mockKeycloakToken)
      .bearerAccessToken

    doAnswer(() => Future(new BearerAccessToken("token")))
      .when(keycloak)
      .serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])

    doAnswer(() => Future(GraphQlResponse[ac.Data](Option(ac.Data(addConsignmentData)), Nil)))
      .when(addConsignmentClient)
      .getResult[Identity](any[BearerAccessToken], any[Document], any[Option[ac.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])

    val response = GraphQlApiService.apply(addConsignmentClient).addConsignment(mockKeycloakToken).unsafeRunSync()

    response.consignmentid.get shouldBe UUID.fromString(consignmentId)
    response.seriesid shouldBe None
  }

  "addConsignment" should "throw an exception when no consignment added" in {
    doAnswer(() => Future(new BearerAccessToken("token")))
      .when(keycloak)
      .serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])

    doAnswer(() => Future(GraphQlResponse[ac.Data](None, Nil)))
      .when(addConsignmentClient)
      .getResult[Identity](any[BearerAccessToken], any[Document], any[Option[ac.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])

    val exception = intercept[RuntimeException] {
      GraphQlApiService.apply(addConsignmentClient).addConsignment(mockKeycloakToken).unsafeRunSync()
    }
    exception.getMessage should equal(s"Consignment not added")
  }
}
