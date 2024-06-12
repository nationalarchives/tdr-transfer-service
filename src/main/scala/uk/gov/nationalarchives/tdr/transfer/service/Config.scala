package uk.gov.nationalarchives.tdr.transfer.service

object Config {
  case class Api(port: Int)
  case class Auth(url: String, realm: String)

  case class Configuration(auth: Auth, api: Api)
}
