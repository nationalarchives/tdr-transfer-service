package uk.gov.nationalarchives.tdr.transfer.service.services.management

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => query}
import org.mockito.ArgumentMatchers.any
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.services.S3Service
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages

import java.util.UUID

class TransferManagementGraphQlSpec extends BaseSpec {
  private val graphQlPath = "/graphql"
  private val wiremockGraphqlServer = new WireMockServer(WireMockConfiguration.options().dynamicPort())

  override def beforeAll(): Unit = {
    super.beforeAll()
    wiremockGraphqlServer.start()
  }

  override def afterAll(): Unit = {
    try wiremockGraphqlServer.stop()
    finally super.afterAll()
  }

  "ingestComplete" should "read actual GraphQL file metadata and send the exported asset ID" in {
    val consignmentId = UUID.randomUUID()
    val owner = UUID.randomUUID()
    val fileId = UUID.randomUUID()
    val assetId = UUID.randomUUID()
    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .willReturn(okJson(s"""{
          "data": {
            "getConsignment": {
              "userid": "$owner",
              "consignmentReference": "TDR-123",
              "files": [{
                "fileId": "$fileId",
                "fileType": "File",
                "fileMetadata": [{"name": "AssetId", "value": "$assetId"}]
              }],
              "consignmentMetadata": []
            }
          }
        }"""))
    )
    val token = mock[Token]
    when(token.userId).thenReturn(owner)
    when(token.transferServiceRoles).thenReturn(Set.empty)
    when(token.bearerAccessToken).thenReturn(new BearerAccessToken("test-token"))
    val messages = mock[Messages]
    when(messages.sendIngestCompleteMessage(any[UUID])).thenReturn(IO.pure(null))
    val s3 = mock[S3Service]
    when(s3.tagBagitPackage("export-bucket", "TDR-123")).thenReturn(IO.unit)
    val client = new GraphQLClient[query.Data, query.Variables](s"${wiremockGraphqlServer.baseUrl()}$graphQlPath")

    new TransferManagement(client, messages, s3, "export-bucket").ingestComplete(token, List(consignmentId)).unsafeRunSync()

    verify(messages).sendIngestCompleteMessage(assetId)
    verify(s3).tagBagitPackage("export-bucket", "TDR-123")
    wiremockGraphqlServer.verify(
      postRequestedFor(urlEqualTo(graphQlPath))
        .withHeader("Authorization", equalTo("Bearer test-token"))
        .withRequestBody(containing(consignmentId.toString))
    )
  }
}
