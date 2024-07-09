package uk.gov.nationalarchives.tdr.transfer.service

import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import pureconfig.generic.auto._

object ApplicationConfig {
  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  case class TransferServiceApi(port: Int)
  case class ConsignmentApi(url: String)
  case class Auth(url: String, realm: String)
  case class S3(metadataUploadBucket: String, recordsUploadBucket: String)

  case class Configuration(auth: Auth, transferServiceApi: TransferServiceApi, consignmentApi: ConsignmentApi, s3: S3)

  val appConfig: Configuration = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load transfer service application configuration ${value.prettyPrint()}")
    case Right(value) => value
  }
}
