package uk.gov.nationalarchives.tdr.transfer.service.api.model

object SourceSystem {
  object SourceSystemEnum extends Enumeration {
    type SourceSystem = Value
    val HardDrive: Value = Value("harddrive")
    val NetworkDrive: Value = Value("networkdrive")
    val SharePoint: Value = Value("sharepoint")
  }
}
