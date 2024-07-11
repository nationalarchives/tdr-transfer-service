package uk.gov.nationalarchives.tdr.transfer.service

import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import pureconfig.generic.auto._

object ApplicationConfig {
  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  case class Api(port: Int)
  case class Auth(url: String, realm: String)
  case class Sns(snsEndpoint: String, notificationsTopicArn: String)
  case class Sfn(dataLoadEndpoint: String)

  case class Configuration(auth: Auth, api: Api, sns: Sns, sfn: Sfn)

  val appConfig: Configuration = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load transfer service application configuration ${value.prettyPrint()}")
    case Right(value) => value
  }
}
