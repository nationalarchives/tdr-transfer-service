package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.{AuthenticationError, AuthorisationError, BackendError, ErrorHandler}

import scala.concurrent.ExecutionContext

case class AuthenticatedContext(token: Token)

class TokenAuthenticator(errorHandler: ErrorHandler)(implicit logger: SelfAwareStructuredLogger[IO]) {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private val appConfig = ApplicationConfig.appConfig

  private val authUrl = appConfig.auth.url
  private val realm = appConfig.auth.realm

  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
    TdrKeycloakDeployment(s"$authUrl", realm, 8080)

  private def parseToken(bearer: String): IO[Either[Throwable, Token]] =
    IO.blocking(KeycloakUtils().token(bearer))

  def authenticateStandardUserToken(bearer: String): IO[Either[BackendError, AuthenticatedContext]] =
    parseToken(bearer).flatMap {
      case Right(t) if t.isStandardUser =>
        IO.pure(Right(AuthenticatedContext(t)))
      case Right(t) if !t.isStandardUser =>
        val errorMessage = s"User ${t.userId} is not a standard user"
        errorHandler.handleErrorAsLeft(AuthorisationError(errorMessage))
      case Right(t) =>
        val errorMessage = s"User ${t.userId} does not have access"
        errorHandler.handleErrorAsLeft(AuthorisationError(errorMessage))
      case Left(e) =>
        errorHandler.handleErrorAsLeft(AuthenticationError(e.getMessage))
    }

  def authenticateClientToken(bearer: String): IO[Either[BackendError, AuthenticatedContext]] =
    parseToken(bearer).flatMap {
      case Right(t) if t.transferServiceRoles.contains("data-load") =>
        IO.pure(Right(AuthenticatedContext(t)))
      case Right(t) =>
        val errorMessage = s"${t.userId} does not have correct authorisation"
        errorHandler.handleErrorAsLeft(AuthorisationError(errorMessage))
      case Left(e) =>
        errorHandler.handleErrorAsLeft(AuthenticationError(e.getMessage))
    }
}

object TokenAuthenticator {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TokenAuthenticator(new ErrorHandler())(logger)
}
