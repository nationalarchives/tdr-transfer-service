package uk.gov.nationalarchives.tdr.transfer.service.api.model

import java.util.UUID

sealed trait NotificationModel

object NotificationModel {
  case class DataLoadMessage(userId: UUID, consignmentId: UUID, consignmentReference: String) extends NotificationModel
}
