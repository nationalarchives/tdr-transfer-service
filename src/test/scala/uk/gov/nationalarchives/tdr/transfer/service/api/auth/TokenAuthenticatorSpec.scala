package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.unsafe.implicits.global
import com.tngtech.keycloakmock.api.TokenConfig.aTokenConfig
import com.tngtech.keycloakmock.api.{KeycloakMock, ServerConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import pureconfig.ConfigSource
import uk.gov.nationalarchives.tdr.transfer.service.Config.Configuration
import pureconfig.generic.auto._

import java.util.UUID

class TokenAuthenticatorSpec extends AnyFlatSpec with Matchers {
  private val config = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load database migration config${value.prettyPrint()}")
    case Right(value) => value
  }

  private val tdrKeycloakMock: KeycloakMock = createServer("tdr", 8000)
  private val testKeycloakMock: KeycloakMock = createServer("test", 8001)
  val userId: UUID = UUID.fromString("4ab14990-ed63-4615-8336-56fbb9960300")

  "'authenticateUserToken'" should "return authenticated token when token valid" in {
    val validToken = validUserToken()
    val response = TokenAuthenticator.apply(config).authenticateUserToken(validToken.token).unsafeRunSync()

    response.toOption.get.token.userId shouldBe userId
  }

  "'authenticateUserToken'" should "return authentication error when token invalid" in {
    val token = invalidToken
    val response = TokenAuthenticator.apply(config).authenticateUserToken(token.token).unsafeRunSync()

    response.left.toOption.get.message shouldBe "Invalid token issuer. Expected 'http://localhost:8000/auth/realms/tdr'"
  }

  private def createServer(realm: String, port: Int): KeycloakMock = {
    val config = ServerConfig.aServerConfig().withPort(port).withDefaultRealm(realm).build()
    val mock: KeycloakMock = new KeycloakMock(config)
    mock.start()
    mock
  }

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

}
