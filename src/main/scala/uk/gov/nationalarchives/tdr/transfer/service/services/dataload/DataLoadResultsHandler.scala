package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.keycloak.KeycloakService
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.NotificationService
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.NotificationService.DataLoadNotificationEvent

import java.util.UUID

class DataLoadResultsHandler(notificationsService: NotificationService, keycloakService: KeycloakService) {
  private val appConfig = ApplicationConfig.appConfig

  def handleResult(consignmentId: UUID): IO[String] = {

    // Send user notification
    for {
      // Get consignment details /statuses
      // Update statuses based on consignment details
      event <- createDataLoadEvent(consignmentId)
      response = notificationsService.sendDataLoadResultNotification(event)
    } yield "OK"

    // Update statuses
  }

  private def createDataLoadEvent(consignmentId: UUID): IO[DataLoadNotificationEvent] = {
    // Get relevant consignment details: consignment reference, user id, statuses
    for {
      userDetails <- keycloakService.userDetails(UUID.randomUUID(), appConfig.transferService.client, appConfig.transferService.clientSecret)
      result = ???
      consignmentReference = ???
    } yield DataLoadNotificationEvent(userDetails.email, result, consignmentReference)
  }
}

object DataLoadResultsHandler {
  def apply() = new DataLoadResultsHandler(NotificationService(), KeycloakService()(HttpURLConnectionBackend()))
}
