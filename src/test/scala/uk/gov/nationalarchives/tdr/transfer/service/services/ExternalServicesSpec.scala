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

  def graphqlOkJson(): Unit = {
    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("addConsignment"))
        .willReturn(okJson(fromResource(s"json/add_consignment_response.json").mkString))
    )

    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("startUpload"))
        .willReturn(okJson(fromResource(s"json/start_upload_response.json").mkString))
    )
  }
}
