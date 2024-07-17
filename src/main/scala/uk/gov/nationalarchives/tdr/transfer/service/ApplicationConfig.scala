package uk.gov.nationalarchives.tdr.transfer.service

import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import pureconfig.generic.auto._

object ApplicationConfig {
  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  case class Api(port: Int)
  case class Auth(url: String, realm: String)
  case class S3(metadataS3SourceBucket: String, metadataS3UploadBucket: String, metadataFileName: String)
  case class Sns(snsEndpoint: String, notificationsTopicArn: String)
  case class Sfn(sfnEndpoint: String, dataLoadStepFunctionArn: String)
  case class TransferService(client: String, clientSecret: String)

  case class Configuration(auth: Auth, api: Api, s3: S3, sns: Sns, sfn: Sfn, transferService: TransferService)

  val appConfig: Configuration = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load transfer service application configuration ${value.prettyPrint()}")
    case Right(value) => value
  }
}
