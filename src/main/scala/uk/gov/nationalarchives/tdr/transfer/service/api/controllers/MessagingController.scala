package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.AuthenticatedContext
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.services.NotificationService

import java.util.UUID

class MessagingController(messagingService: NotificationService) extends BaseController {
  val consignmentId: EndpointInput[UUID] = path("consignmentId")
  def endpoints: List[Endpoint[String, Unit, BackendException.AuthenticationError, String, Any]] = List(dataLoadResultEndpoint.endpoint)

  def routes: HttpRoutes[IO] = dataLoadResultRoute

  private val dataLoadResultEndpoint: PartialServerEndpoint[String, AuthenticatedContext, UUID, BackendException.AuthenticationError, String, Any, IO] = securedWithBearer
    .summary("Notify result of data load")
    .post
    .in("messaging" / "dataload" / "result" / consignmentId)
    .out(jsonBody[String])

  val dataLoadResultRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(dataLoadResultEndpoint.serverLogicSuccess(ac => _ => IO("OK")))
}

object MessagingController {
  def apply() = new MessagingController(NotificationService())
}
