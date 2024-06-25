package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.tapir.{Endpoint, _}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.AuthenticatedContext
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Consignment.ConsignmentDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.services.ConsignmentService

class LoadController(consignmentService: ConsignmentService) extends BaseController {
  def endpoints: List[Endpoint[String, Unit, BackendException.AuthenticationError, ConsignmentDetails, Any]] = List(initiateLoadEndpoint.endpoint)

  def routes: HttpRoutes[IO] = initiateLoadRoute

  val initiateLoadEndpoint: PartialServerEndpoint[String, AuthenticatedContext, Unit, BackendException.AuthenticationError, ConsignmentDetails, Any, IO] = securedWithBearer
    .summary("Initiate the load of records and metadata")
    .post
    .in("load" / "sharepoint" / "initiate")
    .out(jsonBody[ConsignmentDetails])

  val initiateLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(initiateLoadEndpoint.serverLogicSuccess(ac => _ => consignmentService.createConsignment(ac.token)))
}

object LoadController {
  def apply() = new LoadController(ConsignmentService.apply())
}
