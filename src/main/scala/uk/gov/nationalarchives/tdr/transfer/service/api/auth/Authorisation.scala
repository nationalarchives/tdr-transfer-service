package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException.{AuthenticationError, AuthorisationError}
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.util.UUID
import scala.concurrent.ExecutionContext

case class AuthorisationContext(token: Token)

class Authorisation(graphQlApiService: GraphQlApiService)(implicit logger: SelfAwareStructuredLogger[IO]) {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private val appConfig = ApplicationConfig.appConfig

  private val authUrl = appConfig.auth.url
  private val realm = appConfig.auth.realm

  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
    TdrKeycloakDeployment(s"$authUrl", realm, 8080)


  def validateUserHasAccessToConsignment(token: Token, transferId: UUID): IO[Unit] = {
    graphQlApiService.getConsignment(token, transferId).flatMap { consignment =>
      if (consignment.userid == token.userId) IO.unit
      else IO.raiseError(BackendException.AuthenticationError("User does not have access to this consignment"))
    }
  }

  def validateUserHasAccessToConsignment(bearer: String, transferId: UUID): IO[Either[AuthorisationError, AuthorisationContext]] = {
    //Temporary logic below.. this would call the graphql service to check user has access to the consignment like above
    IO {
      KeycloakUtils().token(bearer) match {
        case Right(t) if t.isStandardUser => Right(AuthorisationContext(t))
        case Right(t)                     =>
          Left {
            val errorMessage = s"User ${t.userId} is not a standard user"
            logger.info(s"Authorisation error: $errorMessage")
            AuthorisationError(errorMessage)
          }
        case Left(e) =>
          Left {
            logger.info(s"Authentication error: ${e.getMessage}")
            AuthorisationError(e.getMessage)
          }
      }
    }
  }
}

object Authorisation {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new Authorisation(GraphQlApiService.service)(logger)
}
