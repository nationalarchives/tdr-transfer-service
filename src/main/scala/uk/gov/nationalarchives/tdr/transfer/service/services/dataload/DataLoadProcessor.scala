package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeByName
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.tdr.common.utils.statecontrol.{CurrentState, TransferState}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.{ClientChecksType, UploadType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.{CompletedValue, FailedValue, InProgressValue, StatusValue}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.{BackendError, ClientSideError, DataLoadError}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{LoadCompletion, LoadCompletionResponse}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.{BaseService, GraphQlApiService}
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadProcessor.{DataLoadProcessorEvent, DataLoadProcessorInput}
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages.AggregateProcessingEvent

import java.util.UUID

class DataLoadProcessor(messageService: Messages, appConfig: ApplicationConfig.Configuration, graphQlApiService: GraphQlApiService)(implicit
    logger: SelfAwareStructuredLogger[IO]
) extends BaseService[DataLoadProcessorInput, LoadCompletionResponse] {

  private def sendProcessMessage(transferId: UUID, token: Token, sourceSystem: SourceSystem, loadSuccess: Boolean, loadedNumberOfFiles: Int): IO[SendMessageResponse] = {
    val metadataSourceObjectPrefix = s"${token.userId}/$sourceSystem/$transferId/metadata"
    val metadataSourceBucket = appConfig.s3.metadataUploadBucketName
    val ignoreSiteNameBodies = appConfig.transferConfiguration.ignoreSiteNameBodies.split(";").toSet
    val transferringBodies: Set[String] = token.transferringBodies.getOrElse(Nil).toSet
    val ignoreSiteName = transferringBodies.exists(ignoreSiteNameBodies.contains)

    val eventMessage = AggregateProcessingEvent(metadataSourceBucket, metadataSourceObjectPrefix, !loadSuccess, ignoreSiteName = ignoreSiteName, loadedNumberOfFiles)
    messageService.sendAggregateProcessingEventMessage(transferId, eventMessage)
  }

  private def isStateCorrect(
      transferId: UUID,
      statuses: List[ConsignmentStatuses],
      statusValue: StatusValue
  ): IO[Option[DataLoadError]] = {
    val clientChecksInProgress =
      statuses.exists(s => s.statusType == ClientChecksType.id && s.value == InProgressValue.value)

    val result = TransferState
      .apply(UploadType)
      .checkStateChange(statusValue, CurrentState(transferId, statuses))
      .fold(
        _ => Some(DataLoadError(s"${UploadType.id} state change ${statusValue.value} rejected for transfer: $transferId")),
        _ =>
          if (clientChecksInProgress) None
          else Some(DataLoadError(s"Transfer state incorrect: $transferId"))
      )

    IO.pure(result)
  }

  def result(input: DataLoadProcessorInput): IO[Either[BackendError, LoadCompletionResponse]] = {
    val event = input.event
    val token = input.token
    val transferId = event.transferId
    val details = event.loadCompletionDetails

    for {
      statuses <- graphQlApiService.consignmentState(token, transferId)
      loadErrors <- hasClientSideErrors(transferId, details)
      uploadStatus = if (loadErrors.isEmpty) CompletedValue else FailedValue
      stateErrors <- isStateCorrect(transferId, statuses, uploadStatus)
      loadSuccess = loadErrors.isEmpty && stateErrors.isEmpty
      _ <- (
        graphQlApiService.updateConsignmentStatus(token, transferId, UploadType, uploadStatus) *>
          logger.info(s"${UploadType.id} status updated to ${uploadStatus.value} for transfer: $transferId")
      ).whenA(stateErrors.isEmpty)
      _ <- (
        sendProcessMessage(transferId, token, event.source, loadSuccess, details.loadedNumberFiles) *>
          logger.info(s"Process message sent for transfer: $transferId")
      ).whenA(loadSuccess)
    } yield loadErrors
      .orElse(stateErrors)
      .toLeft(LoadCompletionResponse(transferId, loadSuccess))
  }

  private def hasClientSideErrors(transferId: UUID, loadCompletionDetails: LoadCompletion): IO[Option[BackendError]] = {
    val clientSideErrors = loadCompletionDetails.loadErrors
    if (clientSideErrors.nonEmpty) {
      IO(Some(ClientSideError(s"Client side data load error(s) for transfer $transferId: ${clientSideErrors.mkString("; ").trim}")))
    } else hasDataLoadErrors(transferId, loadCompletionDetails)
  }

  private def hasDataLoadErrors(transferId: UUID, loadCompletionDetails: LoadCompletion): IO[Option[BackendError]] = {
    val errorMessage = loadCompletionDetails match {
      case _ if loadCompletionDetails.expectedNumberFiles != loadCompletionDetails.loadedNumberFiles => Some(s"Expected files does not match actual files: $transferId")
      case _ if loadCompletionDetails.loadedNumberFiles < 1                                          => Some(s"No files uploaded: $transferId")
      case _                                                                                         => None
    }
    errorMessage match {
      case Some(value) => IO(Some(DataLoadError(value)))
      case _           => IO.pure(None)
    }
  }
}

object DataLoadProcessor {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  private val messageService = Messages()(logger)
  val config: ApplicationConfig.Configuration = ApplicationConfig.appConfig
  case class DataLoadProcessorInput(event: DataLoadProcessorEvent, token: Token)
  case class DataLoadProcessorEvent(source: SourceSystem, transferId: UUID, metadataOnly: Option[Boolean] = None, loadCompletionDetails: LoadCompletion)

  def apply() = new DataLoadProcessor(messageService, config, GraphQlApiService.service)(logger)
}
