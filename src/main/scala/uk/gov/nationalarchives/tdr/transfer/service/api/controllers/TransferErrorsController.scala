package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.{AuthenticationError, BackendError, ErrorHandler}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferErrorResultsModel.TransferErrorsResults
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors

import java.util.UUID

class TransferErrorsController(
    transferErrors: TransferErrors,
    authorisation: Authorisation,
    errorHandler: ErrorHandler
) extends BaseController {
  private val transferFunction: EndpointInput[TransferFunction] = path("transferFunction")

  override def routes: HttpRoutes[IO] = getErrorsRoute

  def endpoints: List[Endpoint[String, (TransferFunction, UUID), BackendError, TransferErrorsResults, Any]] =
    List(getErrorsEndpoint.endpoint)

  private val getErrorsEndpoint: PartialServerEndpoint[String, AuthenticatedContext, (TransferFunction, UUID), BackendError, TransferErrorsResults, Any, IO] =
    securedWithStandardUserBearer
      .summary("Retrieve transfer errors for a given transfer")
      .description("Returns a list of transfer errors for the specified transfer ID")
      .in(s"${TransferFunction.Errors}" / transferFunction / transferId)
      .out(jsonBody[TransferErrorsResults])

  val getErrorsRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      getErrorsEndpoint.serverLogic { ac =>
        { case (transferFunction, transferId) =>
          lazy val prefix = s"$transferId/$transferFunction"

          val authorisationIO: IO[Unit] = authorisation.validateUserConsignmentAccess(ac.token, transferId)

          val resultIO: IO[TransferErrorsResults] = transferErrors.getTransferErrors(ac.token, transferId, prefix)

          (for {
            _ <- authorisationIO
            result <- resultIO
          } yield Right(result)).handleErrorWith {
            case err: BackendError =>
              errorHandler.handleErrorAsLeft(err)
            case err =>
              IO.raiseError(err)
          }
        }
      }
    )
}

object TransferErrorsController {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) =
    new TransferErrorsController(TransferErrors(), Authorisation(), new ErrorHandler())
}
