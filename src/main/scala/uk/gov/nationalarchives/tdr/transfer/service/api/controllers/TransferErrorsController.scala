package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferErrorModel.TransferErrorsResults
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors

import java.util.UUID

class TransferErrorsController(transferErrors: TransferErrors)(implicit logger: SelfAwareStructuredLogger[IO]) extends BaseController {

  override def routes: HttpRoutes[IO] = getErrorsRoute

  def endpoints: List[Endpoint[String, UUID, BackendException.AuthenticationError, TransferErrorsResults, Any]] =
    List(getErrorsEndpoint.endpoint)

  private val getErrorsEndpoint: PartialServerEndpoint[String, AuthenticatedContext, UUID, BackendException.AuthenticationError, TransferErrorsResults, Any, IO] =
    securedWithStandardUserBearer
      .summary("Retrieve transfer errors for a given transfer")
      .description("Returns a list of transfer errors for the specified transfer ID")
      .in("errors" / "load" / transferId)
      .out(jsonBody[TransferErrorsResults])

  val getErrorsRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      getErrorsEndpoint.serverLogic { ac => transferId =>
        (for {
          _ <- Authorisation().validateUserHasAccessToConsignment(ac.token, transferId)
          result <- transferErrors.getTransferErrors(ac.token, Some(transferId))
        } yield Right(result)).handleErrorWith {
          case ex: BackendException.AuthenticationError => IO.pure(Left(ex))
          case ex                                       => IO.pure(Left(BackendException.AuthenticationError(ex.getMessage)))
        }
      }
    )
}

object TransferErrorsController {

  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TransferErrorsController(TransferErrors())
}
