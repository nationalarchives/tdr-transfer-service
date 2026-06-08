package uk.gov.nationalarchives.tdr.transfer.service.api.model

object Common {
  object TransferFunction extends Enumeration {
    type TransferFunction = Value
    val Errors: Value = Value("errors")
    val Load: Value = Value("load")
  }
}
