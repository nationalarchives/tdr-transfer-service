package uk.gov.nationalarchives.tdr.transfer.service.api.errors

sealed trait BackendError extends Exception {
  val message: String
}

object BackendError {
  case class AuthenticationError(message: String) extends BackendError
  case class SeriesAssignmentError(message: String) extends BackendError
  case class TransferStateError(message: String) extends BackendError
}
