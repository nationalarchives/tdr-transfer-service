package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import graphql.codegen.GetConsignment.{getConsignment => gc}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.common.utils.authorisation.{Allow, ConsignmentAuthorisation, ConsignmentAuthorisationInput, Deny}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.TransferServiceServer.backend
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class Authorisation(authorisationModule: ConsignmentAuthorisation) {

  def validateUserHasAccessToConsignment(token: Token, transferId: UUID): IO[Unit] = {
    val input = ConsignmentAuthorisationInput(transferId, token)
    authorisationModule.hasAccess(input).flatMap { result =>
      if (result == Allow) IO.unit else IO.raiseError(BackendException.AuthenticationError(s"User ${token.userId} does not have access to consignment: $transferId"))
    }
  }
}

object Authorisation {
  private val apiUrl = appConfig.consignmentApi.url
  private val client = new GraphQLClient[gc.Data, gc.Variables](apiUrl)
  def apply() = new Authorisation(new ConsignmentAuthorisation(client))
}
