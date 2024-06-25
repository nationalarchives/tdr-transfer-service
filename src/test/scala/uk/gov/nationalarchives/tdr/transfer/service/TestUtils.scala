package uk.gov.nationalarchives.tdr.transfer.service

import com.tngtech.keycloakmock.api.TokenConfig.aTokenConfig
import com.tngtech.keycloakmock.api.{KeycloakMock, ServerConfig}
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.Configuration

import java.util.UUID

object TestUtils extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  private val tdrKeycloakMock: KeycloakMock = createServer("tdr", 8000)
  private val testKeycloakMock: KeycloakMock = createServer("test", 8001)

  val config = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load database migration config${value.prettyPrint()}")
    case Right(value) => value
  }

  val userId: UUID = UUID.fromString("4ab14990-ed63-4615-8336-56fbb9960300")

  def validUserToken(userId: UUID = userId, body: String = "Code", standardUser: String = "true"): OAuth2BearerToken =
    OAuth2BearerToken(
      tdrKeycloakMock.getAccessToken(
        aTokenConfig()
          .withResourceRole("tdr", "tdr_user")
          .withClaim("body", body)
          .withClaim("user_id", userId)
          .withClaim("standard_user", standardUser)
          .build
      )
    )

  def invalidToken: OAuth2BearerToken = OAuth2BearerToken(testKeycloakMock.getAccessToken(aTokenConfig().build))

  private def createServer(realm: String, port: Int): KeycloakMock = {
    val config = ServerConfig.aServerConfig().withPort(port).withDefaultRealm(realm).build()
    val mock: KeycloakMock = new KeycloakMock(config)
    mock.start()
    mock
  }
}
