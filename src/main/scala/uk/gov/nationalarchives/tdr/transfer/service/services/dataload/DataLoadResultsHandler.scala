package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import sttp.client3.HttpURLConnectionBackend
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.keycloak.KeycloakService
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.NotificationService
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.NotificationService.DataLoadNotificationEvent

import java.util.UUID

class DataLoadResultsHandler(
    notificationsService: NotificationService,
    keycloakService: KeycloakService,
    graphqlApiService: GraphQlApiService
) {
  private val appConfig = ApplicationConfig.appConfig

  def handleResult(consignmentId: UUID): IO[String] = {
    val transferServiceConfig = appConfig.transferService
    // Send user notification
    for {
      // Get consignment details /statuses
      accessToken <- keycloakService.serviceAccountToken(appConfig.transferService.client, appConfig.transferService.clientSecret)
      consignmentDetails <- graphqlApiService.consignmentDetails(accessToken, consignmentId)
      userDetails <- keycloakService.userDetails(UUID.randomUUID(), transferServiceConfig.client, transferServiceConfig.clientSecret)
      result = ???
      consignmentReference = consignmentDetails.get.consignmentReference
      event = DataLoadNotificationEvent(userDetails.email, result, consignmentReference)
      response = notificationsService.sendDataLoadResultNotification(event)
      // Update statuses based on consignment details
    } yield "OK"
  }
}

object DataLoadResultsHandler {
  def apply() = new DataLoadResultsHandler(NotificationService(), KeycloakService()(HttpURLConnectionBackend()), GraphQlApiService.service)
}
