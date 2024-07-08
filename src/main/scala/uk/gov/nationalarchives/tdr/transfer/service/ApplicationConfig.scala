package uk.gov.nationalarchives.tdr.transfer.service

import pureconfig.ConfigSource
import pureconfig.generic.auto._

object ApplicationConfig {
  case class Api(port: Int)
  case class Auth(url: String, realm: String)

  case class Configuration(auth: Auth, api: Api)

  val appConfig: Configuration = ConfigSource.default.load[Configuration] match {
    case Left(value)  => throw new RuntimeException(s"Failed to load transfer service application configuration ${value.prettyPrint()}")
    case Right(value) => value
  }
}
