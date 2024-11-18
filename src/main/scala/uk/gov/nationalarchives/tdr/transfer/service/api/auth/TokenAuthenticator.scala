package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException.AuthenticationError

import scala.concurrent.ExecutionContext

case class AuthenticatedContext(token: Token)

class TokenAuthenticator()(implicit logger: SelfAwareStructuredLogger[IO]) {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private val appConfig = ApplicationConfig.appConfig

  private val authUrl = appConfig.auth.url
  private val realm = appConfig.auth.realm

  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
    TdrKeycloakDeployment(s"$authUrl", realm, 8080)

  def authenticateUserToken(bearer: String): IO[Either[AuthenticationError, AuthenticatedContext]] = {
    IO {
      KeycloakUtils().token(bearer) match {
        case Right(t) if t.isStandardUser => Right(AuthenticatedContext(t))
        case Right(t) =>
          Left {
            val errorMessage = s"User ${t.userId} is not a standard user"
            logger.info(s"Authorisation error: $errorMessage")
            AuthenticationError(errorMessage)
          }
        case Left(e) =>
          Left {
            logger.info(s"Authentication error: ${e.getMessage}")
            AuthenticationError(e.getMessage)
          }
      }
    }
  }
}

object TokenAuthenticator {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TokenAuthenticator()(logger)
}
