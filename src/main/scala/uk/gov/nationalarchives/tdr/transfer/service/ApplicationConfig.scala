package uk.gov.nationalarchives.tdr.transfer.service

import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import pureconfig.generic.auto._

object ApplicationConfig {
  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  case class TransferServiceApi(port: Int, throttleAmount: Int, throttlePerMs: Int)
  case class ConsignmentApi(url: String)
  case class Auth(url: String, realm: String)
  case class S3(
      awsRegion: String,
      metadataUploadBucketArn: String,
      metadataUploadBucketName: String,
      recordsUploadBucketArn: String,
      recordsUploadBucketName: String,
      endpoint: String,
      transferErrorsBucketName: String,
      aclHeaderValue: String,
      ifNoneMatchHeaderValue: String
  )
  case class Schema(dataLoadSharePointLocation: String, hardDriveLocation: String, networkDriveLocation: String)
  case class TransferConfiguration(maxNumberRecords: Int, maxIndividualFileSizeMb: Int, maxTransferSizeMb: Int)
  case class Cors(permittedOrigins: List[String])
  case class Sqs(endpoint: String, aggregateProcessingQueueUrl: String)
  case class FeatureAccessBlocks(blockApiDocumentation: Boolean, blockServiceEndpoints: Boolean)

  case class Configuration(
      auth: Auth,
      transferServiceApi: TransferServiceApi,
      consignmentApi: ConsignmentApi,
      s3: S3,
      schema: Schema,
      transferConfiguration: TransferConfiguration,
      cors: Cors,
      sqs: Sqs,
      featureAccessBlocks: FeatureAccessBlocks
  )

  val appConfig: Configuration = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load transfer service application configuration ${value.prettyPrint()}")
    case Right(value) => value
  }
}
