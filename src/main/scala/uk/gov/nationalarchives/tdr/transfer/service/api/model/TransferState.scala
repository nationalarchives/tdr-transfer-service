package uk.gov.nationalarchives.tdr.transfer.service.api.model

object TransferState {
  object StateTypeEnum extends Enumeration {
    type StateType = Value
    val Upload: Value = Value("Upload")
  }

  object StateValueEnum extends Enumeration {
    type StateValue = Value
    val Completed: Value = Value("Completed")
    val CompletedWithIssues: Value = Value("CompletedWithIssues")
    val Failed: Value = Value("Failed")
  }
}
