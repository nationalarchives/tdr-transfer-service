package uk.gov.nationalarchives.tdr.transfer.service.api.errors

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger

sealed trait BackendError extends Exception {
  val message: String
}

object BackendError {
  case class AuthenticationError(message: String) extends BackendError
  case class SeriesAssignmentError(message: String) extends BackendError
  case class TransferStateError(message: String) extends BackendError
}

class ErrorHandler()(implicit logger: SelfAwareStructuredLogger[IO]) {
  def handleError(error: BackendError): IO[Unit] = {
    logger.info(error.message).as(error).flatMap(IO.raiseError)
  }
}
