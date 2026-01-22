package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import io.circe._
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, AuthorisationService}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.RetrieveErrors

import java.util.UUID

class ErrorController(retrieveErrors: RetrieveErrors)(implicit logger: SelfAwareStructuredLogger[IO]) extends BaseController {
  private val existingTransferId: EndpointInput[Option[UUID]] = query("transferId")

  override def routes: HttpRoutes[IO] = getErrorsRoute

  def endpoints: List[Endpoint[String, (SourceSystem, Option[UUID]), BackendException.AuthenticationError, List[Json], Any]] =
    List(getErrorsEndpoint.endpoint)

  /*
    SecurityIn (String) the raw security input type (here a bearer token string).
    SecuredContext (AuthenticatedContext) the authenticated principal/context produced by the security logic and passed to handlers.
    RequestIn ((SourceSystem, UUID)) the request input captured from the endpoint path/body — here a tuple of SourceSystem and UUID (transferId).
    ErrorOut (BackendException.AuthenticationError) the error type returned on failures (authentication/business errors) for this partial endpoint.
    SuccessOut (List[Json]) the success response type (here returned as stringBody).
    Streams (Any) placeholder for streaming/effect-agnostic endpoints (common to leave as Any).
    Effect (IO) the effect type the server logic runs in (Cats-Effect IO).
   */
  private val getErrorsEndpoint: PartialServerEndpoint[String, AuthenticatedContext, (SourceSystem, Option[UUID]), BackendException.AuthenticationError, List[Json], Any, IO] =
    securedWithStandardUserBearer
      .summary("Triggers relevant processing")
      .description("Depending on the provided type and state will start the necessary processing of the transfer")
      .in("load" / sourceSystem / "errors" / existingTransferId)
      .out(jsonBody[List[Json]])

  private val getErrorsRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      getErrorsEndpoint.serverLogicSuccess { ac => input =>
        val transferId = input._2
        for {
          _ <- AuthorisationService().validateUserHasAccessToConsignment(ac.token, transferId.get)
          result <- retrieveErrors.getErrorsFromS3(ac.token, transferId)
        } yield result
      }
    )
}

object ErrorController {

  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new ErrorController(RetrieveErrors())
}
