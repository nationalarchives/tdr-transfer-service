package uk.gov.nationalarchives.tdr.transfer.service.api.errors

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger

sealed trait BackendError extends Exception {
  val message: String
}

case class TransferStateError(message: String) extends BackendError
case class AuthenticationError(message: String) extends BackendError
case class SeriesAssignmentError(message: String) extends BackendError

class ErrorHandler()(implicit logger: SelfAwareStructuredLogger[IO]) {
  private def handleError(error: BackendError): IO[BackendError] = {
    logger.error(error.message).as(error)
  }

  def handleErrorAsLeft(error: BackendError): IO[Left[BackendError, Nothing]] = {
    handleError(error).map(Left(_))
  }
}
