package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import uk.gov.nationalarchives.tdr.transfer.service.services.NotificationService
import uk.gov.nationalarchives.tdr.transfer.service.services.NotificationService.DataLoadNotificationEvent

import java.util.UUID

class DataLoadResultsHandler(notificationsService: NotificationService) {

  def handleResult(consignmentId: UUID): IO[String] = {
    // Send user notification
    val event = createDataLoadEvent(consignmentId)
    notificationsService.sendDataLoadResultNotification(event)
    // Update statuses???
    IO("OK")
  }

  private def createDataLoadEvent(consignmentId: UUID): DataLoadNotificationEvent = {
    // Get relevant consignment details: consignment reference, user id, statuses
    val userEmail = ???
    val result = ???
    val consignmentReference = ???

    DataLoadNotificationEvent(userEmail, result, consignmentReference)
  }
}

object DataLoadResultsHandler {
  def apply() = new DataLoadResultsHandler(NotificationService())
}
