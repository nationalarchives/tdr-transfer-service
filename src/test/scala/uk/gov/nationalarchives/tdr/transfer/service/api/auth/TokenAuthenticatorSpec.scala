package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, userId, validUserToken}

class TokenAuthenticatorSpec extends AnyFlatSpec with Matchers {
  "'authenticateUserToken'" should "return authenticated token when token valid" in {
    val validToken = validUserToken()
    val response = TokenAuthenticator().authenticateUserToken(validToken.token).unsafeRunSync()

    response.toOption.get.token.userId shouldBe userId
  }

  "'authenticateUserToken'" should "return authentication error when token invalid" in {
    val token = invalidToken
    val response = TokenAuthenticator().authenticateUserToken(token.token).unsafeRunSync()

    response.left.toOption.get.message shouldBe "Invalid token issuer. Expected 'http://localhost:8000/auth/realms/tdr'"
  }
}
