package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import io.circe._
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors

import java.util.UUID

class TransferErrorsController(transferErrors: TransferErrors)(implicit logger: SelfAwareStructuredLogger[IO]) extends BaseController {

  override def routes: HttpRoutes[IO] = getErrorsRoute

  def endpoints: List[Endpoint[String, (SourceSystem, UUID), BackendException.AuthenticationError, List[Json], Any]] =
    List(getErrorsEndpoint.endpoint)

  private val getErrorsEndpoint: PartialServerEndpoint[String, AuthenticatedContext, (SourceSystem, UUID), BackendException.AuthenticationError, List[Json], Any, IO] =
    securedWithStandardUserBearer
      .summary("Retrieve transfer errors for a given transfer")
      .description("Returns a list of transfer errors for the specified transfer ID")
      .in("load" / sourceSystem / "errors" / transferId)
      .out(jsonBody[List[Json]])

  val getErrorsRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      getErrorsEndpoint.serverLogicSuccess { ac => input =>
        val transferId = input._2
        for {
          _ <- Authorisation().validateUserHasAccessToConsignment(ac.token, transferId)
          result <- transferErrors.getErrorsFromS3(ac.token, Some(transferId))
        } yield result
      }
    )
}

object TransferErrorsController {

  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TransferErrorsController(TransferErrors())
}
