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

  private def authenticationErrorHandler(errorMessage: String, errorType: String = "Authorisation"): AuthenticationError = {
    logger.info(s"$errorType error: $errorMessage")
    AuthenticationError(errorMessage)
  }

  private def transferringBodies(token: Token): List[String] = {
    token.transferringBodies match {
      case Some(bodies) => bodies
      case _            => Nil
    }
  }

  def authenticateStandardUserToken(bearer: String): IO[Either[AuthenticationError, AuthenticatedContext]] = {
    IO {
      KeycloakUtils().token(bearer) match {
        case Right(t) if t.isStandardUser && transferringBodies(t).nonEmpty => Right(AuthenticatedContext(t))
        case Right(t) if !t.isStandardUser                                  =>
          Left {
            val errorMessage = s"User ${t.userId} is not a standard user"
            authenticationErrorHandler(errorMessage)
          }
        case Right(t) if transferringBodies(t).isEmpty =>
          Left {
            val errorMessage = s"User ${t.userId} is not assigned to a transferring body"
            authenticationErrorHandler(errorMessage)
          }
        case Right(t) =>
          Left {
            val errorMessage = s"User ${t.userId} does not have access"
            authenticationErrorHandler(errorMessage)
          }
        case Left(e) =>
          Left {
            authenticationErrorHandler(e.getMessage, "Authentication")
          }
      }
    }
  }

  def authenticateClientToken(bearer: String): IO[Either[AuthenticationError, AuthenticatedContext]] = {
    IO {
      KeycloakUtils().token(bearer) match {
        case Right(t) if t.transferServiceRoles.contains("data-load") => Right(AuthenticatedContext(t))
        case Right(t)                                                 =>
          Left {
            val errorMessage = s"${t.userId} does not have correct authorisation"
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
