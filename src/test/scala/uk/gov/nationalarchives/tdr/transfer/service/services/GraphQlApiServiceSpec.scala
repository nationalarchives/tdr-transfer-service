package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.unsafe.implicits.global
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.AddConsignment.addConsignment.AddConsignment
import graphql.codegen.AddConsignment.{addConsignment => ac}
import org.mockito.ArgumentMatchers.any
import sangria.ast.Document
import sttp.client3.{Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.Future
import scala.reflect.ClassTag

class GraphQlApiServiceSpec extends BaseSpec {

  val mockKeycloakToken: Token = mock[Token]
  val keycloak: KeycloakUtils = mock[KeycloakUtils]
  val addConsignmentClient: GraphQLClient[ac.Data, ac.Variables] = mock[GraphQLClient[ac.Data, ac.Variables]]
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
