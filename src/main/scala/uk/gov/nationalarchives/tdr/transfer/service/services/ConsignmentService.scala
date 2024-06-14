package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeId
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Consignment.ConsignmentDetails

import java.util.UUID

class ConsignmentService {
  def createConsignment(token: Token): IO[ConsignmentDetails] = {
    // For now just return dummy response
    ConsignmentDetails(UUID.fromString("ae4b7cad-ee83-46bd-b952-80bc8263c6c2")).pure[IO]
  }
}

object ConsignmentService {
  def apply() = new ConsignmentService
}
