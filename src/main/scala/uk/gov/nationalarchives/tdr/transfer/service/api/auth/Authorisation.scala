package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import graphql.codegen.GetConsignment.{getConsignment => gc}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.common.utils.authorisation.{Allow, ConsignmentAuthorisation, ConsignmentAuthorisationInput, Deny}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.TransferServiceServer.backend
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class Authorisation(authorisationModule: ConsignmentAuthorisation)(implicit logger: SelfAwareStructuredLogger[IO]) {

  private def authenticationError(errorMessage: String): IO[BackendError.AuthenticationError] =
    logger.info(s"Authorisation error: $errorMessage").as(BackendError.AuthenticationError(errorMessage))

  def validateUserHasAccessToConsignment(token: Token, transferId: UUID): IO[Unit] = {
    val input = ConsignmentAuthorisationInput(transferId, token)
    authorisationModule.hasAccess(input).flatMap {
      case Allow => IO.unit
      case Deny  =>
        val errorMessage = s"User ${token.userId} does not have access to consignment: $transferId"
        authenticationError(errorMessage).flatMap(IO.raiseError)
    }
  }
}

object Authorisation {
  private val apiUrl = appConfig.consignmentApi.url
  private val client = new GraphQLClient[gc.Data, gc.Variables](apiUrl)
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new Authorisation(new ConsignmentAuthorisation(client))
}
