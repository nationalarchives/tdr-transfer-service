package uk.gov.nationalarchives.tdr.transfer.service.api.errors

sealed trait BackendError extends Exception {
  val message: String
}

object BackendError {
  case class AuthenticationError(message: String) extends BackendError
}
