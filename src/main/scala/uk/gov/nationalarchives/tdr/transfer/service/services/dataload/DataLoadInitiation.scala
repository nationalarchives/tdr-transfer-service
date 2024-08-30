package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails, TransferConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.{s3Config, transferConfigurationConfig}

import java.util.UUID

class DataLoadInitiation(graphQlApiService: GraphQlApiService) {
  def initiateConsignmentLoad(token: Token, sourceSystem: SourceSystem): IO[LoadDetails] = {
    for {
      addConsignmentResult <- graphQlApiService.addConsignment(token)
      consignmentId = addConsignmentResult.consignmentid.get
      _ <- graphQlApiService.startUpload(token, consignmentId)
      result <- loadDetails(consignmentId, token.userId, sourceSystem)
    } yield result
  }

  private def loadDetails(transferId: UUID, userId: UUID, sourceSystem: SourceSystem): IO[LoadDetails] = {
    val s3KeyPrefix = s"$userId/$sourceSystem/$transferId"
    val maxNumberRecords = transferConfigurationConfig.maxNumberRecords
    val recordsS3Bucket = AWSS3LoadDestination(s"${s3Config.recordsUploadBucket}", s"$s3KeyPrefix/records")
    val metadataS3Bucket = AWSS3LoadDestination(s"${s3Config.metadataUploadBucket}", s"$s3KeyPrefix/metadata")
    val metadataProperties = MetadataLoadConfiguration.metadataLoadConfiguration(sourceSystem)
    val transferConfiguration = TransferConfiguration(maxNumberRecords, metadataProperties)
    IO(LoadDetails(transferId, recordsLoadDestination = recordsS3Bucket, metadataLoadDestination = metadataS3Bucket, transferConfiguration))
  }
}

object DataLoadInitiation {
  val s3Config: ApplicationConfig.S3 = ApplicationConfig.appConfig.s3
  val transferConfigurationConfig: ApplicationConfig.TransferConfiguration = ApplicationConfig.appConfig.transferConfiguration
  def apply() = new DataLoadInitiation(GraphQlApiService.service)
}
