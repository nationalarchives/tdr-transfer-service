package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.util.UUID

class Authorisation(graphQlApiService: GraphQlApiService)(implicit logger: SelfAwareStructuredLogger[IO]) {

  def validateUserHasAccessToConsignment(token: Token, transferId: UUID): IO[Unit] = {
    graphQlApiService.getConsignment(token, transferId).flatMap { consignment =>
      if (consignment.userid == token.userId) IO.unit
      else IO.raiseError(BackendException.AuthenticationError("User does not have access to this consignment"))
    }
  }
}

object Authorisation {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new Authorisation(GraphQlApiService.service)(logger)
}
