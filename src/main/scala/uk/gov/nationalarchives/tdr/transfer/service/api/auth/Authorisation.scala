package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.util.UUID

class Authorisation(graphQlApiService: GraphQlApiService) {

  def validateUserHasAccessToConsignment(token: Token, transferId: UUID): IO[Unit] = {
    graphQlApiService.getConsignment(token, transferId).flatMap { consignment =>
      if (consignment.userid == token.userId) IO.unit
      else IO.raiseError(BackendException.AuthenticationError(s"User does not have access to consignment: $transferId"))
    }
  }
}

object Authorisation {
  def apply() = new Authorisation(GraphQlApiService.service)
}
