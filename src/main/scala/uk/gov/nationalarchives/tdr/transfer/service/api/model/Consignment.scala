package uk.gov.nationalarchives.tdr.transfer.service.api.model

import java.util.UUID

sealed trait ConsignmentModel

object Consignment {
  case class ConsignmentDetails(consignmentId: UUID) extends ConsignmentModel
}
