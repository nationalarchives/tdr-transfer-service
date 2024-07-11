package uk.gov.nationalarchives.tdr.transfer.service.services

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.nationalarchives.aws.utils.sns.SNSClients.sns
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.NotificationService.DataLoadEvent

class NotificationService {
  private val snsConfig = ApplicationConfig.appConfig.sns
  private val client: SnsClient = sns(snsConfig.snsEndpoint)
  private val utils: SNSUtils = SNSUtils(client)

  implicit val transferCompletedEventEncoder: Encoder[DataLoadEvent] = deriveEncoder[DataLoadEvent]

  def sendDataLoadResultNotification(dataLoadEvent: DataLoadEvent): PublishResponse = {
    utils.publish(dataLoadEvent.asJson.toString(), snsConfig.notificationsTopicArn)
  }
}

object NotificationService {
  case class DataLoadEvent(
                            userEmail: String,
                            result: String,
                            consignmentReference: String
                          )
  def apply() = new NotificationService
}
