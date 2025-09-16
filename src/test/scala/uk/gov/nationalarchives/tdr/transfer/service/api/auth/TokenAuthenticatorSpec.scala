package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.unsafe.implicits.global
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, userId, validUserToken}

class TokenAuthenticatorSpec extends BaseSpec {
  "'authenticateStandardUserToken'" should "return authenticated token when token valid" in {
    val validToken = validUserToken()
    val response = TokenAuthenticator().authenticateStandardUserToken(validToken.token).unsafeRunSync()

    response.toOption.get.token.userId shouldBe userId
  }

  "'authenticateStandardUserToken'" should "return authentication error when user is not a standard user" in {
    val validToken = validUserToken(standardUser = "false")
    val response = TokenAuthenticator().authenticateStandardUserToken(validToken.token).unsafeRunSync()

    response.left.toOption.get.message shouldBe s"User $userId is not a standard user"
  }

  "'authenticateStandardUserToken'" should "return authentication error when token invalid" in {
    val token = invalidToken
    val response = TokenAuthenticator().authenticateStandardUserToken(token.token).unsafeRunSync()

    response.left.toOption.get.message shouldBe "Invalid token issuer. Expected 'http://localhost:8000/auth/realms/tdr'"
  }
}
