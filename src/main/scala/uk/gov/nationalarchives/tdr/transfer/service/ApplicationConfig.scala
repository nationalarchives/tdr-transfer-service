package uk.gov.nationalarchives.tdr.transfer.service

object ApplicationConfig {
  case class TransferServiceApi(port: Int)
  case class ConsignmentApi(url: String)
  case class Auth(url: String, realm: String)

  case class Configuration(auth: Auth, transferServiceApi: TransferServiceApi, consignmentApi: ConsignmentApi)
}
