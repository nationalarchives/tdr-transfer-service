package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.tdr.common.utils.statecontrol.CurrentState
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.{ClientChecksType, UploadType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.{CompletedValue, FailedValue, InProgressValue, StatusValue}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{LoadCompletion, LoadCompletionResponse}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadProcessor.DataLoadProcessorEvent
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages.AggregateProcessingEvent
import uk.gov.nationalarchives.tdr.common.utils.statecontrol.{CurrentState, StateChange, TransferState, ValidStateChange}

import java.util.UUID

class DataLoadProcessor(messageService: Messages, appConfig: ApplicationConfig.Configuration, graphQlApiService: GraphQlApiService)(implicit
    logger: SelfAwareStructuredLogger[IO]
) {

  private def sendProcessMessage(transferId: UUID, token: Token, sourceSystem: SourceSystem, loadSuccess: Boolean): SendMessageResponse = {
    val metadataSourceObjectPrefix = s"${token.userId}/$sourceSystem/$transferId/metadata"
    val metadataSourceBucket = appConfig.s3.metadataUploadBucketName
    val ignoreSiteNameBodies = appConfig.transferConfiguration.ignoreSiteNameBodies.split(";").toSet
    val ignoreSiteName = ignoreSiteNameBodies.contains(token.transferringBody.get)

    logger.info(s"Triggering aggregate processing for transfer: $transferId")
    val eventMessage = AggregateProcessingEvent(metadataSourceBucket, metadataSourceObjectPrefix, !loadSuccess, ignoreSiteName = ignoreSiteName)
    messageService.sendAggregateProcessingEventMessage(transferId, eventMessage)
  }

  private def isStateCorrect(transferId: UUID, statuses: List[ConsignmentStatuses], statusValue: StatusValue): Boolean = {
    TransferState
      .apply(UploadType)
      .checkStateChange(statusValue, CurrentState(transferId, statuses))
      .fold(
        _ => {
          false
        },
        _ => {
          val clientChecksStatus = statuses.find(_.statusType == ClientChecksType.id)
          clientChecksStatus.exists(_.value == InProgressValue.value)
        }
      )
  }

  def trigger(event: DataLoadProcessorEvent, token: Token): IO[LoadCompletionResponse] = {
    val transferId = event.transferId
    for {
      statuses <- graphQlApiService.consignmentState(token, transferId)
      clientSideErrors = hasClientSideErrors(transferId, event.loadCompletionDetails)
      dataLoadErrors = hasDataLoadErrors(event.loadCompletionDetails)
      uploadStatus =
        if (!dataLoadErrors && !clientSideErrors) { CompletedValue }
        else FailedValue
      stateCorrect = isStateCorrect(transferId, statuses, uploadStatus)
      loadSuccess = stateCorrect && !dataLoadErrors && !clientSideErrors
      loadCompletionResponse = LoadCompletionResponse(transferId, loadSuccess)
      _ <- if (stateCorrect) graphQlApiService.updateConsignmentStatus(token, transferId, UploadType, uploadStatus) else IO.unit
      _ = if (!clientSideErrors) {
        sendProcessMessage(transferId, token, event.source, loadSuccess)
      }
    } yield loadCompletionResponse
  }

  private def hasClientSideErrors(transferId: UUID, loadCompletionDetails: LoadCompletion): Boolean = {
    val clientSideErrors = loadCompletionDetails.loadErrors
    if (clientSideErrors.nonEmpty) {
      logger.info(s"Client side data load error(s) for transfer $transferId: ${clientSideErrors.mkString("; ").trim}")
      true
    } else false
  }

  private def hasDataLoadErrors(loadCompletionDetails: LoadCompletion): Boolean = {
    (loadCompletionDetails.expectedNumberFiles != loadCompletionDetails.loadedNumberFiles) || loadCompletionDetails.loadedNumberFiles < 1
  }
}

object DataLoadProcessor {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  private val messageService = Messages()(logger)
  val config: ApplicationConfig.Configuration = ApplicationConfig.appConfig
  case class DataLoadProcessorEvent(source: SourceSystem, transferId: UUID, metadataOnly: Option[Boolean] = None, loadCompletionDetails: LoadCompletion)

  def apply() = new DataLoadProcessor(messageService, config, GraphQlApiService.service)(logger)
}
