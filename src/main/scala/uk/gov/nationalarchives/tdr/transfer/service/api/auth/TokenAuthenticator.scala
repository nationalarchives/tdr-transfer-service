package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import sttp.client3.HttpURLConnectionBackend
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException.AuthenticationError
import uk.gov.nationalarchives.tdr.transfer.service.services.keycloak.KeycloakService

case class AuthenticatedContext(token: Token)

class TokenAuthenticator(keycloakService: KeycloakService) {
  def authenticateUserToken(bearer: String): IO[Either[AuthenticationError, AuthenticatedContext]] = {
    IO {
      keycloakService.accessToken(bearer) match {
        case Right(t) => Right(AuthenticatedContext(t))
        case Left(e)  => Left(AuthenticationError(e.getMessage))
      }
    }
  }
}

object TokenAuthenticator {
  def apply() = new TokenAuthenticator(KeycloakService()(HttpURLConnectionBackend()))
}
