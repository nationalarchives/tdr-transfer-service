package uk.gov.nationalarchives.tdr.transfer.service.services

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.io.File
import scala.io.Source.fromResource
import scala.reflect.io.Directory

class ExternalServicesSpec extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val wiremockGraphqlServer = new WireMockServer(9001)
  val wiremockAuthServer = new WireMockServer(9002)
//  val wiremockSsmServer = new WireMockServer(9004)
//  val wiremockS3 = new WireMockServer(8003)

//  def setupSsmServer(): Unit = {
//    wiremockSsmServer
//      .stubFor(
//        post(urlEqualTo("/"))
//          .willReturn(okJson("{\"Parameter\":{\"Name\":\"string\",\"Value\":\"string\"}}"))
//      )
//  }

  val graphQlPath = "/graphql"
  val authPath = "/auth/realms/tdr/protocol/openid-connect/token"

  def graphQlUrl: String = wiremockGraphqlServer.url(graphQlPath)

  def graphqlOkJson(saveMetadata: Boolean = false): Unit = {
    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("addConsignment"))
        .willReturn(okJson(fromResource(s"json/add_consignment_response.json").mkString))
    )

    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("displayProperties"))
        .willReturn(okJson(fromResource(s"json/display_properties.json").mkString))
    )

    wiremockGraphqlServer.stubFor(
      post(urlEqualTo(graphQlPath))
        .withRequestBody(containing("updateConsignmentStatus"))
        .willReturn(ok("""{"data": {"updateConsignmentStatus": 1}}""".stripMargin))
    )
    if (saveMetadata) {
      wiremockGraphqlServer.stubFor(
        post(urlEqualTo(graphQlPath))
          .withRequestBody(containing("addOrUpdateBulkFileMetadata"))
          .willReturn(ok("""{"data": {"addOrUpdateBulkFileMetadata": []}}""".stripMargin))
      )
    }
  }

  def authOkJson(): StubMapping = wiremockAuthServer.stubFor(
    post(urlEqualTo(authPath))
      .willReturn(okJson("""{"access_token": "abcde"}"""))
  )

  def authUnavailable: StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath)).willReturn(serverError()))

  def graphqlUnavailable: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath)).willReturn(serverError()))

  override def beforeAll(): Unit = {
    wiremockGraphqlServer.start()
    wiremockAuthServer.start()
  }

  override def afterAll(): Unit = {
    wiremockGraphqlServer.stop()
    wiremockAuthServer.stop()
  }

  override def afterEach(): Unit = {
    wiremockAuthServer.resetAll()
    wiremockGraphqlServer.resetAll()
    val runningFiles = new File(s"./src/test/resources/testfiles/running-files/")
    if (runningFiles.exists()) {
      new Directory(runningFiles).deleteRecursively()
    }
  }
}
