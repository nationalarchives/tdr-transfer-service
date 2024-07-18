package uk.gov.nationalarchives.tdr.transfer.service

import com.tngtech.keycloakmock.api.TokenConfig.aTokenConfig
import com.tngtech.keycloakmock.api.{KeycloakMock, ServerConfig}
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import java.util.UUID

object TestUtils {
  private val tdrKeycloakMock: KeycloakMock = createServer("tdr", 8000)
  private val testKeycloakMock: KeycloakMock = createServer("test", 8001)

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
