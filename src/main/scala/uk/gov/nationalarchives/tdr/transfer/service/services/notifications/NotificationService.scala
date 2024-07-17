package uk.gov.nationalarchives.tdr.transfer.service.services.notifications

import cats.effect.IO
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.nationalarchives.aws.utils.sns.SNSClients.sns
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.NotificationService.DataLoadNotificationEvent

class NotificationService {
  private val snsConfig = ApplicationConfig.appConfig.sns
  private val client: SnsClient = sns(snsConfig.snsEndpoint)
  private val utils: SNSUtils = SNSUtils(client)

  implicit val dataLoadEventEncoder: Encoder[DataLoadNotificationEvent] = deriveEncoder[DataLoadNotificationEvent]

  def sendDataLoadResultNotification(dataLoadEvent: DataLoadNotificationEvent): IO[PublishResponse] = {
    for {
      result <- IO(utils.publish(dataLoadEvent.asJson.toString(), snsConfig.notificationsTopicArn))
    } yield result
  }
}

object NotificationService {
  sealed trait NotificationEvent

  case class DataLoadNotificationEvent(
      userEmail: String,
      result: String,
      consignmentReference: String
  ) extends NotificationEvent

  def apply() = new NotificationService
}
