package uk.gov.nationalarchives.tdr.transfer.service.services.notifications

import cats.effect.IO
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.nationalarchives.aws.utils.sns.SNSClients.sns
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Notifications.UploadEvent

class Notifications(snsUtils: SNSUtils, snsConfig: ApplicationConfig.Sns)(implicit logger: SelfAwareStructuredLogger[IO]) {
  implicit val uploadEventEncoder: Encoder[UploadEvent] = deriveEncoder[UploadEvent]

  def sendUploadEventUserEmail(event: UploadEvent): PublishResponse = {
    val topicArn = snsConfig.userEmailSnsTopicArn
    val snsMessage = event.asJson.toString()
    publishSnsNotification(snsMessage, topicArn)
  }

  private def publishSnsNotification(message: String, topicArn: String): PublishResponse = {
    snsUtils.publish(message, topicArn)
  }
}

object Notifications {
  private val snsConfig = ApplicationConfig.appConfig.sns
  private val snsClient: SnsClient = sns(snsConfig.endpoint)
  val snsUtils: SNSUtils = SNSUtils(snsClient)

  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new Notifications(snsUtils, snsConfig)(logger)

  trait Notification
  case class UploadEvent(transferringBodyName: String, consignmentReference: String, consignmentId: String, userId: String, userEmail: String, status: String) extends Notification
}
