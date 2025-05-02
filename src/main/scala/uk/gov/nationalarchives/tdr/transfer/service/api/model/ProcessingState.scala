package uk.gov.nationalarchives.tdr.transfer.service.api.model

object ProcessingState {
  object ProcessingStateEnum extends Enumeration {
    type ProcessingState = Value
    val Completed: Value = Value("completed")
    val CompletedWithIssues: Value = Value("completedwithissues")
    val Failed: Value = Value("failed")
    val InProgress: Value = Value("inprogress")
  }
}
