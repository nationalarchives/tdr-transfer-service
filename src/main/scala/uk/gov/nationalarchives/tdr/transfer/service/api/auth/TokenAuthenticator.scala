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

  private def authenticationErrorHandler(errorMessage: String, errorType: String = "Authorisation"): IO[AuthenticationError] =
    logger.info(s"$errorType error: $errorMessage").as(AuthenticationError(errorMessage))

  def authenticateStandardUserToken(bearer: String): IO[Either[AuthenticationError, AuthenticatedContext]] = {
    KeycloakUtils().token(bearer) match {
      case Right(t) if t.isStandardUser  => IO.pure(Right(AuthenticatedContext(t)))
      case Right(t) if !t.isStandardUser =>
        val errorMessage = s"User ${t.userId} is not a standard user"
        authenticationErrorHandler(errorMessage).map(Left(_))
      case Right(t) =>
        val errorMessage = s"User ${t.userId} does not have access"
        authenticationErrorHandler(errorMessage).map(Left(_))
      case Left(e) =>
        authenticationErrorHandler(e.getMessage, "Authentication").map(Left(_))
    }
  }

  def authenticateClientToken(bearer: String): IO[Either[AuthenticationError, AuthenticatedContext]] = {
    KeycloakUtils().token(bearer) match {
      case Right(t) if t.transferServiceRoles.contains("data-load") => IO.pure(Right(AuthenticatedContext(t)))
      case Right(t)                                                 =>
        val errorMessage = s"${t.userId} does not have correct authorisation"
        logger.info(s"Authorisation error: $errorMessage").as(Left(AuthenticationError(errorMessage)))
      case Left(e) =>
        logger.info(s"Authentication error: ${e.getMessage}").as(Left(AuthenticationError(e.getMessage)))
    }
  }
}

object TokenAuthenticator {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TokenAuthenticator()(logger)
}
