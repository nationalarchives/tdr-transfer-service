package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.ConsignmentStatusType.Upload
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.ObjectCategory.{MetadataCategory, RecordsCategory}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.StatusValue.InProgress
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.s3Config

import java.util.UUID

class DataLoadInitiation(graphQlApiService: GraphQlApiService)(implicit logger: SelfAwareStructuredLogger[IO]) {
  def initiateConsignmentLoad(token: Token, sourceSystem: SourceSystem, existingTransferId: Option[UUID] = None): IO[LoadDetails] = {
    if (existingTransferId.nonEmpty) {
      for {
        consignmentState <- graphQlApiService.consignmentState(token, existingTransferId.get)
        loadDetails <-
          loadDetailsForExistingTransfer(token, existingTransferId.get, sourceSystem, canUpload(consignmentState.consignmentStatuses))
      } yield loadDetails
    } else {
      logger.info(s"Creating consignment for user ${token.userId} from ${sourceSystem.toString}")
      for {
        addConsignmentResult <- graphQlApiService.addConsignment(token, sourceSystem)
        consignmentId = addConsignmentResult.consignmentid.get
        _ <- triggerUpload(token, consignmentId)
        result <- loadDetails(consignmentId, addConsignmentResult.consignmentReference, token.userId, sourceSystem)
      } yield result
    }
  }

  private def loadDetails(transferId: UUID, transferReference: String, userId: UUID, sourceSystem: SourceSystem): IO[LoadDetails] = {
    val s3KeyPrefix = s"$userId/$sourceSystem/$transferId"
    val awsRegion = s3Config.awsRegion
    val recordsS3Bucket =
      AWSS3LoadDestination(s"$awsRegion", s"${s3Config.recordsUploadBucketArn}", s"${s3Config.recordsUploadBucketName}", s"$s3KeyPrefix/${RecordsCategory.toString}")
    val metadataS3Bucket =
      AWSS3LoadDestination(s"$awsRegion", s"${s3Config.metadataUploadBucketArn}", s"${s3Config.metadataUploadBucketName}", s"$s3KeyPrefix/${MetadataCategory.toString}")
    IO(LoadDetails(transferId, transferReference, recordsLoadDestination = recordsS3Bucket, metadataLoadDestination = metadataS3Bucket))
  }

  private def canUpload(state: List[ConsignmentStatuses]): Boolean = {
    val uploadState: Option[ConsignmentStatuses] = state.find(_.statusType == Upload.toString)
    uploadState.nonEmpty && uploadState.get.value == InProgress.toString
  }

  private def loadDetailsForExistingTransfer(token: Token, consignmentId: UUID, sourceSystem: SourceSystem, canUpload: Boolean) = {
    if (canUpload) {
      for {
        summary <- graphQlApiService.existingConsignment(token, consignmentId)
        result <- loadDetails(consignmentId, summary.consignmentReference, token.userId, sourceSystem)
      } yield result
    } else IO.raiseError(throw new RuntimeException(s"Consignment not in state to allow upload: $consignmentId"))

  }

  private def triggerUpload(token: Token, consignmentId: UUID): IO[Unit] = {
    logger.info(s"Starting upload for consignment $consignmentId")
    for {
      _ <- graphQlApiService.startUpload(token, consignmentId)
    } yield IO.unit
  }
}

object DataLoadInitiation {
  val s3Config: ApplicationConfig.S3 = ApplicationConfig.appConfig.s3
  val transferConfigurationConfig: ApplicationConfig.TransferConfiguration = ApplicationConfig.appConfig.transferConfiguration
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new DataLoadInitiation(GraphQlApiService.service)(logger)
}
