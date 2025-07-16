package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.unsafe.implicits.global
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, transferServiceUserId, userId, validClientToken, validUserToken}

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

  "'authenticateClientToken'" should "return authenticated transfer service client token when token valid" in {
    val validToken = validClientToken()
    val response = TokenAuthenticator().authenticateClientToken(validToken.token).unsafeRunSync()

    response.toOption.get.token.userId shouldBe transferServiceUserId
  }

  "'authenticateClientToken'" should "return authentication error when transfer service client does not have the 'data-load' role" in {
    val validToken = validClientToken(role = "some-role")
    val response = TokenAuthenticator().authenticateClientToken(validToken.token).unsafeRunSync()

    response.left.toOption.get.message shouldBe s"$transferServiceUserId does not have correct authorisation"
  }

  "'authenticateClientToken'" should "return authentication error when client is not the transfer service client" in {
    val validToken = validClientToken(clientId = "random-client-id")
    val response = TokenAuthenticator().authenticateClientToken(validToken.token).unsafeRunSync()

    response.left.toOption.get.message shouldBe s"$transferServiceUserId does not have correct authorisation"
  }

  "'authenticateClientToken'" should "return authentication error when token invalid" in {
    val token = invalidToken
    val response = TokenAuthenticator().authenticateClientToken(token.token).unsafeRunSync()

    response.left.toOption.get.message shouldBe "Invalid token issuer. Expected 'http://localhost:8000/auth/realms/tdr'"
  }
}
