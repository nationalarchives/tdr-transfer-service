package uk.gov.nationalarchives.tdr.transfer.service.api.model

object Common {
  object ConsignmentStatusType extends Enumeration {
    type ConsignmentStatusType = Value
    val Upload: Value = Value("Upload")
  }

  object StatusValue extends Enumeration {
    type ConsignmentStatusValue = Value
    val Completed: Value = Value("Completed")
    val InProgress: Value = Value("InProgress")
  }

  object ObjectCategory extends Enumeration {
    type ObjectCategory = Value
    val MetadataCategory: Value = Value("metadata")
    val RecordsCategory: Value = Value("records")
  }

  object TransferFunction extends Enumeration {
    type TransferFunction = Value
    val Load: Value = Value("load")
  }
}
