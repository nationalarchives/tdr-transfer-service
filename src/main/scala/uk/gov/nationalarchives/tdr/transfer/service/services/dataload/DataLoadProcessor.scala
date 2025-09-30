package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{LoadCompletion, LoadCompletionResponse}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadProcessor.DataLoadProcessorEvent
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages.AggregateProcessingEvent

import java.util.UUID

class DataLoadProcessor(messageService: Messages, s3Config: ApplicationConfig.S3)(implicit logger: SelfAwareStructuredLogger[IO]) {
  def trigger(event: DataLoadProcessorEvent, token: Token): IO[LoadCompletionResponse] = {
    val metadataSourceBucket = s3Config.metadataUploadBucketName
    val transferId = event.transferId
    val details = event.loadCompletionDetails
    val metadataSourceObjectPrefix = s"${token.userId}/${event.source}/$transferId/metadata"
    val clientSideErrors = checkClientSideErrors(transferId, details)
    val dataLoadErrors = checkDataLoadErrors(details)

    if (!clientSideErrors) {
      logger.info(s"Triggering aggregate processing for transfer: $transferId")
      val eventMessage = AggregateProcessingEvent(metadataSourceBucket, metadataSourceObjectPrefix, dataLoadErrors)
      messageService.sendAggregateProcessingEventMessage(transferId, eventMessage)
    }
    IO(LoadCompletionResponse(transferId, !clientSideErrors && !dataLoadErrors))
  }

  private def checkClientSideErrors(transferId: UUID, loadCompletionDetails: LoadCompletion): Boolean = {
    val clientSideErrors = loadCompletionDetails.loadErrors
    if (clientSideErrors.nonEmpty) {
      logger.info(s"Client side data load error(s) for transfer $transferId: ${clientSideErrors.mkString("; ").trim}")
      true
    } else false
  }

  private def checkDataLoadErrors(loadCompletionDetails: LoadCompletion): Boolean = {
    (loadCompletionDetails.expectedNumberFiles != loadCompletionDetails.loadedNumberFiles) || loadCompletionDetails.loadedNumberFiles < 1
  }
}

object DataLoadProcessor {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  private val messageService = Messages()(logger)
  val s3Config: ApplicationConfig.S3 = ApplicationConfig.appConfig.s3
  case class DataLoadProcessorEvent(source: SourceSystem, transferId: UUID, metadataOnly: Option[Boolean] = None, loadCompletionDetails: LoadCompletion)

  def apply() = new DataLoadProcessor(messageService, s3Config)(logger)
}
