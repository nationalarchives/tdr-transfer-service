package uk.gov.nationalarchives.tdr.transfer.service

object ApplicationConfig {
  case class Api(port: Int, url: String)
  case class Auth(url: String, realm: String)

  case class Configuration(auth: Auth, api: Api)
}
