package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.unsafe.implicits.global
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.AddConsignment.addConsignment.AddConsignment
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.AddOrUpdateConsignmenetMetadata.{addOrUpdateConsignmentMetadata => acm}
import graphql.codegen.StartUpload.{startUpload => su}
import org.mockito.ArgumentMatchers.any
import sangria.ast.Document
import sttp.client3.{Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.Future
import scala.reflect.ClassTag

class GraphQlApiServiceSpec extends BaseSpec {

  val mockKeycloakToken: Token = mock[Token]
  val keycloak: KeycloakUtils = mock[KeycloakUtils]
  val addConsignmentClient: GraphQLClient[ac.Data, ac.Variables] = mock[GraphQLClient[ac.Data, ac.Variables]]
  val consignmentMetadataClient: GraphQLClient[acm.Data, acm.Variables] = mock[GraphQLClient[acm.Data, acm.Variables]]
  val startUploadClient: GraphQLClient[su.Data, su.Variables] = mock[GraphQLClient[su.Data, su.Variables]]
  val consignmentId = "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"
  val userId: UUID = UUID.fromString("4ab14990-ed63-4615-8336-56fbb9960300")
  val consignmentMetadataData = acm.AddOrUpdateConsignmentMetadata(UUID.fromString(consignmentId), "SourceSystem", SourceSystemEnum.SharePoint.toString)
  val addConsignmentData = AddConsignment(Some(UUID.fromString(consignmentId)), None, "Consignment-Ref")

  "'addConsignment'" should "return the consignment id of the created consignment" in {
    mockKeycloak()
    mockAddConsignmentClient(Some(ac.Data(addConsignmentData)))
    mockConsignmentMetadataClient(Some(acm.Data(List(consignmentMetadataData))))

    val response = GraphQlApiService
      .apply(addConsignmentClient, consignmentMetadataClient, startUploadClient)
      .addConsignment(mockKeycloakToken, SourceSystemEnum.SharePoint)
      .unsafeRunSync()

    response.consignmentid.get shouldBe UUID.fromString(consignmentId)
    response.seriesid shouldBe None
  }

  "'addConsignment'" should "throw an exception when no consignment added" in {
    mockKeycloak()
    mockAddConsignmentClient(None)
    mockConsignmentMetadataClient(Some(acm.Data(List(consignmentMetadataData))))

    when(mockKeycloakToken.userId).thenReturn(userId)

    val exception = intercept[RuntimeException] {
      GraphQlApiService
        .apply(addConsignmentClient, consignmentMetadataClient, startUploadClient)
        .addConsignment(mockKeycloakToken, SourceSystemEnum.SharePoint)
        .unsafeRunSync()
    }
    exception.getMessage should equal(s"Consignment not added for user 4ab14990-ed63-4615-8336-56fbb9960300")
  }

  "'addConsignment'" should "throw an exception when consignment metadata not added" in {
    mockKeycloak()
    mockAddConsignmentClient(Some(ac.Data(addConsignmentData)))
    mockConsignmentMetadataClient(None)

    when(mockKeycloakToken.userId).thenReturn(userId)

    val exception = intercept[RuntimeException] {
      GraphQlApiService
        .apply(addConsignmentClient, consignmentMetadataClient, startUploadClient)
        .addConsignment(mockKeycloakToken, SourceSystemEnum.SharePoint)
        .unsafeRunSync()
    }
    exception.getMessage should equal(s"Consignment metadata not added for 6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  }

  "'startUpload'" should "return parent folder name if provided" in {
    val startUploadData = "parentFolder"

    mockKeycloak()
    mockStartUploadClient(Some(su.Data(startUploadData)))

    val response =
      GraphQlApiService
        .apply(addConsignmentClient, consignmentMetadataClient, startUploadClient)
        .startUpload(mockKeycloakToken, UUID.fromString(consignmentId), Some("parentFolder"))
        .unsafeRunSync()

    response shouldBe startUploadData
  }

  "'startUpload'" should "return empty string if no parent folder provided" in {
    mockKeycloak()
    mockStartUploadClient(Some(su.Data("")))

    val response = GraphQlApiService
      .apply(addConsignmentClient, consignmentMetadataClient, startUploadClient)
      .startUpload(mockKeycloakToken, UUID.fromString(consignmentId))
      .unsafeRunSync()

    response shouldBe ""
  }

  "'startUpload'" should "throw an exception when no consignment added" in {
    mockKeycloak()
    mockStartUploadClient(None)

    val exception = intercept[RuntimeException] {
      GraphQlApiService
        .apply(addConsignmentClient, consignmentMetadataClient, startUploadClient)
        .startUpload(mockKeycloakToken, UUID.fromString(consignmentId))
        .unsafeRunSync()
    }
    exception.getMessage should equal(s"Load not started for consignment: 6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  }

  def mockKeycloak(): Future[BearerAccessToken] = {
    doAnswer(() => Future(new BearerAccessToken("token")))
      .when(keycloak)
      .serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])
  }

  def mockAddConsignmentClient(response: Option[ac.Data]): Future[GraphQlResponse[ac.Data]] = {
    doAnswer(() => Future(GraphQlResponse[ac.Data](response, Nil)))
      .when(addConsignmentClient)
      .getResult[Identity](any[BearerAccessToken], any[Document], any[Option[ac.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])
  }

  def mockConsignmentMetadataClient(response: Option[acm.Data]): Future[GraphQlResponse[acm.Data]] = {
    doAnswer(() => Future(GraphQlResponse[acm.Data](response, Nil)))
      .when(consignmentMetadataClient)
      .getResult[Identity](any[BearerAccessToken], any[Document], any[Option[acm.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])
  }

  def mockStartUploadClient(response: Option[su.Data]): Future[GraphQlResponse[su.Data]] = {
    doAnswer(() => Future(GraphQlResponse[su.Data](response, Nil)))
      .when(startUploadClient)
      .getResult[Identity](any[BearerAccessToken], any[Document], any[Option[su.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])
  }
}
