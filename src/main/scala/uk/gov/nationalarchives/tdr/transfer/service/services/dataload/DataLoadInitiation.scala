package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.common.utils.objectkeycontext.ObjectCategories.{Metadata, Records}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.{SeriesType, UploadType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.CompletedValue
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.LoadInitiationException.{SeriesAssignmentError, TransferStateError}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.{SharePoint, SourceSystem}
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.{s3Config, transferConfigurationConfig}

import java.util.UUID

class DataLoadInitiation(graphQlApiService: GraphQlApiService)(implicit logger: SelfAwareStructuredLogger[IO]) {
  def initiateConsignmentLoad(token: Token, sourceSystem: SourceSystem, existingTransferId: Option[UUID] = None): IO[LoadDetails] = {
    for {
      userTransfers <- graphQlApiService.getAllUserConsignments(token)
      _ <- canInitiate(userTransfers, token.userId, existingTransferId)
        .flatMap(_.fold(err => IO.raiseError(throw BackendException.LoadError(err.getMessage)), _ => IO.unit))
      result <-
        if (existingTransferId.nonEmpty) { initiateExistingTransfer(token, sourceSystem, existingTransferId.get) }
        else { initiateNewTransfer(token, sourceSystem) }
    } yield result
  }

  private def canInitiate(userTransfers: List[Node], userId: UUID, existingTransferId: Option[UUID]): IO[Either[Exception, Boolean]] = {
    val missingSeriesCount = userTransfers.count(t => {
      !t.consignmentStatuses.map(_.statusType).contains(SeriesType.id)
    })
    if (missingSeriesCount > transferConfigurationConfig.maxNumberNoSeriesAssignment) {
      val errorMessage = s"User $userId has too many consignments without series assigned"
      logger.error(errorMessage).as(Left(SeriesAssignmentError(errorMessage)))
    } else isTransferStateCorrect(existingTransferId, userTransfers)
  }

  private def isTransferStateCorrect(existingTransferId: Option[UUID], userTransfers: List[Node]): IO[Either[TransferStateError, Boolean]] = {
    val existingTransfer = userTransfers.find(_.consignmentid == existingTransferId)
    val existingTransferStatuses = if (existingTransfer.nonEmpty) { existingTransfer.get.consignmentStatuses }
    else Nil
    val uploadState = existingTransferStatuses.find(_.statusType == UploadType.id)
    uploadState match {
      case Some(state) if state.value == CompletedValue.value =>
        val errorMessage = s"Existing consignment state incorrect for upload: ${existingTransferId.get}"
        logger
          .error(s"Existing consignment state incorrect for upload: ${existingTransferId.get}")
          .as(Left(TransferStateError(errorMessage)))
      case _ => IO.pure(Right(true))
    }
  }

  private def initiateNewTransfer(token: Token, sourceSystem: SourceSystem): IO[LoadDetails] = {
    for {
      _ <- logger.info(s"Creating consignment for user ${token.userId} from ${sourceSystem.toString}")
      addConsignmentResult <- graphQlApiService.addConsignment(token, sourceSystem)
      consignmentId = addConsignmentResult.consignmentid.get
      _ <- triggerUpload(token, consignmentId, sourceSystem)
      result <- loadDetails(consignmentId, addConsignmentResult.consignmentReference, token.userId, sourceSystem)
    } yield result
  }

  private def initiateExistingTransfer(token: Token, sourceSystem: SourceSystem, existingTransferId: UUID): IO[LoadDetails] = {
    for {
      _ <- logger.info(s"Initiating existing consignment: $existingTransferId from ${sourceSystem.toString}")
      summary <- graphQlApiService.existingConsignment(token, existingTransferId)
      result <- loadDetails(existingTransferId, summary.consignmentReference, token.userId, sourceSystem)
    } yield result
  }

  private def loadDetails(transferId: UUID, transferReference: String, userId: UUID, sourceSystem: SourceSystem): IO[LoadDetails] = {
    val s3KeyPrefix = s"$userId/$sourceSystem/$transferId"
    val awsRegion = s3Config.awsRegion
    val recordsS3Bucket =
      AWSS3LoadDestination(s"$awsRegion", s"${s3Config.recordsUploadBucketArn}", s"${s3Config.recordsUploadBucketName}", s"$s3KeyPrefix/${Records.id}")
    val metadataS3Bucket =
      AWSS3LoadDestination(s"$awsRegion", s"${s3Config.metadataUploadBucketArn}", s"${s3Config.metadataUploadBucketName}", s"$s3KeyPrefix/${Metadata.id}")
    IO(LoadDetails(transferId, transferReference, recordsLoadDestination = recordsS3Bucket, metadataLoadDestination = metadataS3Bucket))
  }

  private def triggerUpload(token: Token, consignmentId: UUID, sourceSystem: SourceSystem): IO[Unit] = {
    val includeTopLevelFolder = includeTopLevelFolderOverride(sourceSystem)
    for {
      _ <- logger.info(s"Starting upload for consignment $consignmentId")
      _ <- graphQlApiService.startUpload(token, consignmentId, includeTopLevelFolder = includeTopLevelFolder)
    } yield IO.unit
  }

  private def includeTopLevelFolderOverride(sourceSystem: SourceSystem): Option[Boolean] = {
    sourceSystem match {
      case SharePoint => Some(transferConfigurationConfig.overrideIncludeTopLevelFolder)
      case _          => None
    }
  }
}

object DataLoadInitiation {
  val s3Config: ApplicationConfig.S3 = ApplicationConfig.appConfig.s3
  val transferConfigurationConfig: ApplicationConfig.TransferConfiguration = ApplicationConfig.appConfig.transferConfiguration
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new DataLoadInitiation(GraphQlApiService.service)(logger)
}
