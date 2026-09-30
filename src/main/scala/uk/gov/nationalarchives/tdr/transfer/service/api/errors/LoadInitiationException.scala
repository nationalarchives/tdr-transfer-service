package uk.gov.nationalarchives.tdr.transfer.service.api.errors

object LoadInitiationException {
  case class SeriesAssignmentError(message: String) extends Exception(message)
  case class TransferStateError(message: String) extends Exception(message)
}
