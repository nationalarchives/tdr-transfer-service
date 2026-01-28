package uk.gov.nationalarchives.tdr.transfer.service.services

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec

import java.nio.charset.StandardCharsets
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

//  def mockS3GetObjectStream(key: String, consignmentId: String, matchId: String, suppliedMetadata: Boolean = false): StubMapping = {
////      defaultAndSuppliedMetadataJsonString(matchId, consignmentId).getBytes("UTF-8")
//    wiremockS3.stubFor(
//      get(urlEqualTo(s"/$key"))
//        .willReturn(aResponse().withStatus(200).withBody(bytes))
//    )
//  }

  def mockS3ListObjects(bucket: String, prefix: String, keys: Seq[String]): StubMapping = {
    val contents = keys
      .map { k =>
        val fullKey = if (prefix.endsWith("/")) s"$prefix$k" else s"$prefix/$k"
        s"""
         |<Contents>
         |  <Key>$fullKey</Key>
         |  <LastModified>2020-01-01T00:00:00.000Z</LastModified>
         |  <ETag>"d41d8cd98f00b204e9800998ecf8427e"</ETag>
         |  <Size>0</Size>
         |  <StorageClass>STANDARD</StorageClass>
         |</Contents>
         |""".stripMargin
      }
      .mkString("\n")

    val xml =
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
         |  <Name>$bucket</Name>
         |  <Prefix>$prefix</Prefix>
         |  <MaxKeys>1000</MaxKeys>
         |  <IsTruncated>false</IsTruncated>
         |  $contents
         |</ListBucketResult>
         |""".stripMargin

    wiremockS3.stubFor(
      get(urlPathEqualTo(s"/$bucket"))
        .withQueryParam("prefix", equalTo(prefix))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/xml")
            .withBody(xml)
        )
    )
  }

  def mockS3GetObject(bucket: String, key: String, body: String, contentType: String = "application/json"): StubMapping = {
    val body =
      s"""
         |{
         |  "consignmentId": "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e",
         |  "errorCode": "AGGREGATE_PROCESSING.CLIENT_DATA_LOAD.FAILURE",
         |  "errorMessage": "Client data load errors for consignment: 6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"
         |}
         |""".stripMargin
    val path = s"/$bucket/$key"
    wiremockS3.stubFor(
      get(urlPathEqualTo(path))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", contentType)
            .withHeader("Content-Length", String.valueOf(body.getBytes(StandardCharsets.UTF_8).length))
            .withHeader("ETag", "\"d41d8cd98f00b204e9800998ecf8427e\"")
            .withBody(body)
        )
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
