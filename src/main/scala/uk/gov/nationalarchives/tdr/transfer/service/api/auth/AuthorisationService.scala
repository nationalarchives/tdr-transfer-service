package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.util.UUID

class AuthorisationService(graphQlApiService: GraphQlApiService)(implicit logger: SelfAwareStructuredLogger[IO]) {

  def validateUserHasAccessToConsignment(token: Token, transferId: UUID): IO[Unit] = {
    for {
      consignment <- graphQlApiService.getConsignment(token, transferId)
      _ <-
        if (consignment.userid == token.userId) IO.unit
        else IO.raiseError(BackendException.AuthenticationError("User does not have access to this consignment"))
    } yield ()
  }
}

object AuthorisationService {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new AuthorisationService(GraphQlApiService.service)(logger)
}
