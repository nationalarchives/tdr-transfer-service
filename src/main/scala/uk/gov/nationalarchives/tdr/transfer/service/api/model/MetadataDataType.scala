package uk.gov.nationalarchives.tdr.transfer.service.api.model

object MetadataDataType {
  object MetadataDataTypeEnum extends Enumeration {
    type MetadataDataType = Value
    val boolean: Value = Value("boolean")
    val date: Value = Value("date")
    val text: Value = Value("text")
    val uuid: Value = Value("uuid")
  }
}
