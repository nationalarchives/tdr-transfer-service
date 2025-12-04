package uk.gov.nationalarchives.tdr.transfer.service.api.model

object ObjectCategory {
  object ObjectCategoryEnum extends Enumeration {
    type ObjectCategory = Value
    val DryRunMetadata: Value = Value("dryrunmetadata")
    val DryRunRecords: Value = Value("dryrunrecords")
    val Metadata: Value = Value("metadata")
    val Records: Value = Value("records")
  }
}
