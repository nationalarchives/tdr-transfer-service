package uk.gov.nationalarchives.tdr.transfer.service

import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import pureconfig.generic.auto._

object ApplicationConfig {
  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  case class TransferServiceApi(port: Int)
  case class ConsignmentApi(url: String)
  case class Auth(url: String, realm: String)
  case class S3(awsRegion: String, metadataUploadBucketArn: String, metadataUploadBucketName: String, recordsUploadBucketArn: String, recordsUploadBucketName: String)
  case class Schema(dataLoadSharePointLocation: String)
  case class TransferConfiguration(maxNumberRecords: Int)

  case class Configuration(auth: Auth, transferServiceApi: TransferServiceApi, consignmentApi: ConsignmentApi, s3: S3, schema: Schema, transferConfiguration: TransferConfiguration)

  val appConfig: Configuration = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load transfer service application configuration ${value.prettyPrint()}")
    case Right(value) => value
  }
}
