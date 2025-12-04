package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import graphql.codegen.AddConsignment.addConsignment.AddConsignment
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.ObjectCategory.ObjectCategoryEnum.{DryRunMetadata, DryRunRecords, Metadata, Records}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.s3Config

import java.util.UUID

class DataLoadInitiation(graphQlApiService: GraphQlApiService)(implicit logger: SelfAwareStructuredLogger[IO]) {
  def initiateConsignmentLoad(token: Token, sourceSystem: SourceSystem, dryRun: Option[Boolean]): IO[LoadDetails] = {
    val isDryRun = dryRun.getOrElse(false)
    if (isDryRun) {
      dryRunLoadDetails(token.userId, sourceSystem)
    } else {
      logger.info(s"Creating consignment for user ${token.userId} from ${sourceSystem.toString}")
      for {
        addConsignmentResult <- graphQlApiService.addConsignment(token, sourceSystem)
        consignmentId = addConsignmentResult.consignmentid.get
        _ <- logger.info(s"Starting upload for consignment $consignmentId")
        _ <- graphQlApiService.startUpload(token, consignmentId)
        result <- loadDetails(addConsignmentResult, token.userId, sourceSystem)
      } yield result
    }
  }

  private def dryRunLoadDetails(userId: UUID, sourceSystem: SourceSystem): IO[LoadDetails] = {
    val dryRunId = UUID.randomUUID()
    logger.info(s"Creating dry run for user $userId from ${sourceSystem.toString}: $dryRunId")
    val transferReference = s"dryRun_$dryRunId"
    val s3KeyPrefix = s"$userId/$sourceSystem/$dryRunId"
    val awsRegion = s3Config.awsRegion
    val recordsS3Bucket = AWSS3LoadDestination(s"$awsRegion", s"${s3Config.recordsUploadBucketArn}", s"${s3Config.recordsUploadBucketName}", s"$s3KeyPrefix/$DryRunRecords")
    val metadataS3Bucket = AWSS3LoadDestination(s"$awsRegion", s"${s3Config.metadataUploadBucketArn}", s"${s3Config.metadataUploadBucketName}", s"$s3KeyPrefix/$DryRunMetadata")
    IO(LoadDetails(dryRunId, transferReference, recordsLoadDestination = recordsS3Bucket, metadataLoadDestination = metadataS3Bucket))
  }

  private def loadDetails(transferDetails: AddConsignment, userId: UUID, sourceSystem: SourceSystem): IO[LoadDetails] = {
    val transferId = transferDetails.consignmentid.get
    val transferReference = transferDetails.consignmentReference
    val s3KeyPrefix = s"$userId/$sourceSystem/$transferId"
    val awsRegion = s3Config.awsRegion
    val recordsS3Bucket = AWSS3LoadDestination(s"$awsRegion", s"${s3Config.recordsUploadBucketArn}", s"${s3Config.recordsUploadBucketName}", s"$s3KeyPrefix/$Records")
    val metadataS3Bucket = AWSS3LoadDestination(s"$awsRegion", s"${s3Config.metadataUploadBucketArn}", s"${s3Config.metadataUploadBucketName}", s"$s3KeyPrefix/$Metadata")
    IO(LoadDetails(transferId, transferReference, recordsLoadDestination = recordsS3Bucket, metadataLoadDestination = metadataS3Bucket))
  }
}

object DataLoadInitiation {
  val s3Config: ApplicationConfig.S3 = ApplicationConfig.appConfig.s3
  val transferConfigurationConfig: ApplicationConfig.TransferConfiguration = ApplicationConfig.appConfig.transferConfiguration
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new DataLoadInitiation(GraphQlApiService.service)(logger)
}
