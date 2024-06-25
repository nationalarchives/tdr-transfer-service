package uk.gov.nationalarchives.tdr.transfer.service.api.errors

sealed trait BackendException extends Exception

object BackendException {
  case class AuthenticationError(message: String) extends BackendException
}
