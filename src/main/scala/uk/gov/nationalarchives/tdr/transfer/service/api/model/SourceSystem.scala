package uk.gov.nationalarchives.tdr.transfer.service.api.model

object SourceSystem {
  object SourceSystemEnum extends Enumeration {
    type SourceSystem = Value
    val SharePoint: Value = Value("sharepoint")
  }
}
