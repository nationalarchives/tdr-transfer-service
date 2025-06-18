package uk.gov.nationalarchives.tdr.transfer.service.api.model

object ProcessingType {
  object ProcessingTypeEnum extends Enumeration {
    type ProcessingType = Value
    val Metadata: Value = Value("metadata")
  }
}
