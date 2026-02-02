package uk.gov.nationalarchives.tdr.transfer.service.services

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec

import scala.io.Source.fromResource

class ExternalServicesSpec extends BaseSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val wiremockGraphqlServer = new WireMockServer(9001)
  val wiremockS3 = new WireMockServer(8003)

  override def beforeAll(): Unit = {
    wiremockGraphqlServer.start()
  }

  override def afterAll(): Unit = {
    wiremockGraphqlServer.stop()
  }

  override def afterEach(): Unit = {
    wiremockGraphqlServer.resetAll()
  }

  val graphQlPath = "/graphql"

  def graphqlOkJson(uploadStatusValue: String = "InProgress", consignmentExists: Boolean = true): Unit = {
    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("getConsignment"))
        .willReturn(ok(getConsignmentResponse))
    )

    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("getConsignmentSummary"))
        .willReturn(okJson(consignmentSummary(consignmentExists)))
    )

    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("getConsignmentStatus"))
        .willReturn(okJson(uploadStatusResponse(uploadStatusValue)))
    )

    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("addConsignment"))
        .willReturn(okJson(fromResource(s"json/add_consignment_response.json").mkString))
    )

    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("addOrUpdateConsignmentMetadata"))
        .willReturn(okJson(fromResource(s"json/consignment_metadata_response.json").mkString))
    )

    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("startUpload"))
        .willReturn(okJson(fromResource(s"json/start_upload_response.json").mkString))
    )
  }

  def getConsignmentResponse: String = {
    s"""{
         |  "data": {
         |    "getConsignment": {
         |      "consignmentId": "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e",
         |      "userid": "4ab14990-ed63-4615-8336-56fbb9960300",
         |      "consignmentReference": "Consignment-Ref",
         |      "consignmentStatuses": []
         |    }
         |  }
         |}""".stripMargin
  }

  private def uploadStatusResponse(uploadStatusValue: String): String = {
    s"""
       | {
       |  "data": {
       |    "getConsignment": {
       |      "consignmentStatuses": [
       |        {
       |          "consignmentStatusId": "31657058-a8f7-4b1a-b2d7-529d212a7718",
       |          "consignmentId": "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e",
       |          "statusType": "Upload",
       |          "value": "$uploadStatusValue",
       |          "createdDatetime": "2020-01-01T09:00:00Z"
       |        }
       |      ]
       |    }
       |  }
       | }
       |""".stripMargin
  }

  private def consignmentSummary(consignmentExists: Boolean): String = {
    if (consignmentExists) {
      s"""
         | {
         |  "data": {
         |    "getConsignment": {
         |      "seriesName": "series-name",
         |      "transferringBodyName": "transferring-body-name",
         |      "totalFiles": 0,
         |      "consignmentReference": "Consignment-Ref"
         |    }
         |  }
         |}
         |""".stripMargin
    } else ""

  }
}
