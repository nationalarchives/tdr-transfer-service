package uk.gov.nationalarchives.tdr.transfer.service.services.notifications

import cats.effect.IO
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.aws.utils.sqs.SQSClients.sqs
import uk.gov.nationalarchives.aws.utils.sqs.SQSUtils
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages.AggregateProcessingEvent

import java.util.UUID

class Messages(sqsUtils: SQSUtils, sqsConfig: ApplicationConfig.Sqs)(implicit logger: SelfAwareStructuredLogger[IO]) {
  implicit val aggregateProcessingMessageEncoder: Encoder[AggregateProcessingEvent] = deriveEncoder[AggregateProcessingEvent]

  def sendAggregateProcessingEventMessage(transferId: UUID, message: AggregateProcessingEvent): SendMessageResponse = {
    val queueUrl = sqsConfig.aggregateProcessingQueueUrl
    val messageBody = message.asJson.toString()
    logger.info(s"Sending aggregate processing event message for transfer: $transferId")
    sendMessage(queueUrl, messageBody)
  }

  private def sendMessage(queueUrl: String, messageBody: String): SendMessageResponse = {
    sqsUtils.send(queueUrl, messageBody)
  }
}

object Messages {
  private val sqsConfig = ApplicationConfig.appConfig.sqs
  private val sqsClient: SqsClient = sqs(sqsConfig.endpoint)
  case class AggregateProcessingEvent(metadataSourceBucket: String, metadataSourceObjectPrefix: String, dataLoadErrors: Boolean = false)
  val sqsUtils: SQSUtils = SQSUtils(sqsClient)
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new Messages(sqsUtils, sqsConfig)(logger)
}
