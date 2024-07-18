package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.s3Config

import java.util.UUID

class DataLoadInitiation(graphQlApiService: GraphQlApiService) {
  def initiateLoad(token: Token): IO[LoadDetails] = {
    for {
      addConsignmentResult <- graphQlApiService.addConsignment(token.bearerAccessToken)
      consignmentId = addConsignmentResult.consignmentid.get
      _ <- graphQlApiService.startUpload(token.bearerAccessToken, consignmentId)
      result <- loadDetails(consignmentId, token.userId)
    } yield result
  }

  private def loadDetails(consignmentId: UUID, userId: UUID): IO[LoadDetails] = {
    val recordsS3Bucket = AWSS3LoadDestination(s"${s3Config.recordsUploadBucket}", s"$userId/$consignmentId")
    val metadataS3Bucket = AWSS3LoadDestination(s"${s3Config.metadataUploadBucket}", s"$consignmentId/dataload/data-load-metadata.csv")
    IO(LoadDetails(consignmentId, recordsLoadDestination = recordsS3Bucket, metadataLoadDestination = metadataS3Bucket))
  }
}

object DataLoadInitiation {
  val s3Config = ApplicationConfig.appConfig.s3
  def apply() = new DataLoadInitiation(GraphQlApiService.service)
}
