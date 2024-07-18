package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionClients.sfnAsyncClient
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadProcessor.{ExecutionInput, s3Config}

import java.util.UUID

class DataLoadProcessor {
  implicit val transferCompletedEventEncoder: Encoder[ExecutionInput] = deriveEncoder[ExecutionInput]

  private val sfnConfig = ApplicationConfig.appConfig.sfn

  def trigger(consignmentId: UUID, token: Token): IO[String] = {
    // Trigger data load step function
    val metadataObjectKey = s"$consignmentId/metadataload/${s3Config.metadataFileName}"
    val input = ExecutionInput(consignmentId, token.userId, s3SourceBucketKey = metadataObjectKey, s3UploadBucketKey = metadataObjectKey, s3QuarantineBucketKey = metadataObjectKey)
    for {
      _ <- StepFunctionUtils.apply(sfnAsyncClient(sfnConfig.sfnEndpoint)).startExecution(sfnConfig.dataLoadStepFunctionArn, input)
    } yield "OK"
  }
}

object DataLoadProcessor {
  val s3Config = ApplicationConfig.appConfig.s3

  case class ExecutionInput(
      consignmentId: UUID,
      userId: UUID,
      s3SourceBucketKey: String,
      s3UploadBucketKey: String,
      s3QuarantineBucketKey: String,
      fileId: String = s3Config.metadataFileName,
      s3SourceBucket: String = s3Config.metadataUploadBucket,
      s3UploadBucket: String = s3Config.metadataUploadBucket,
      draftMetadataType: String = "dataLoad"
  )
  def apply() = new DataLoadProcessor
}
