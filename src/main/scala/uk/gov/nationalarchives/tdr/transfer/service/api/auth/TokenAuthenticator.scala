package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.Configuration
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException.AuthenticationError

import scala.concurrent.ExecutionContext

case class AuthenticatedContext(token: Token)

class TokenAuthenticator(config: Configuration) {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val authUrl = config.auth.url
  private val realm = config.auth.realm

  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
    TdrKeycloakDeployment(s"$authUrl", realm, 8080)

  def authenticateUserToken(bearer: String): IO[Either[AuthenticationError, AuthenticatedContext]] = {
    IO {
      KeycloakUtils().token(bearer) match {
        case Right(t) => Right(AuthenticatedContext(t))
        case Left(e) =>
          Left {
            println(e.getMessage)
            AuthenticationError(e.getMessage)
          }
      }
    }
  }
}

object TokenAuthenticator {
  def apply(config: Configuration) = new TokenAuthenticator(config)
}
