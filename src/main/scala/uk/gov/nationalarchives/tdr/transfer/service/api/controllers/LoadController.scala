package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.AuthenticatedContext
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Consignment.ConsignmentDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.{DataLoadProcessor, DataLoadResultsHandler}
import uk.gov.nationalarchives.tdr.transfer.service.services.graphqlapi.ConsignmentService

import java.util.UUID

class LoadController(consignmentService: ConsignmentService, dataLoadProcessing: DataLoadProcessor, dataLoadResultsHandler: DataLoadResultsHandler) extends BaseController {
  def endpoints: List[Endpoint[String, _ >: Unit with UUID, BackendException.AuthenticationError, _ >: ConsignmentDetails with String <: Serializable, Any]] =
    List(initiateLoadEndpoint.endpoint, completeLoadEndpoint.endpoint, processLoadResultEndpoint.endpoint)

  def routes: HttpRoutes[IO] = initiateLoadRoute <+> completeLoadRoute <+> processLoadResultRoute

  private val initiateLoadEndpoint: PartialServerEndpoint[String, AuthenticatedContext, Unit, BackendException.AuthenticationError, ConsignmentDetails, Any, IO] = securedWithBearer
    .summary("Initiate the load of records and metadata")
    .description("Creates consignment in TDR and returns load details to calling client to allow uploading of data to TDR")
    .post
    .in("load" / "sharepoint" / "initiate")
    .out(jsonBody[ConsignmentDetails])

  private val completeLoadEndpoint: PartialServerEndpoint[String, AuthenticatedContext, UUID, BackendException.AuthenticationError, String, Any, IO] = securedWithBearer
    .summary("Notify that loading has completed")
    .description("Triggers the processing of the consignment's loaded metadata and records in TDR")
    .post
    .in("load" / "sharepoint" / "complete" / consignmentId)
    .out(jsonBody[String])

  private val processLoadResultEndpoint: PartialServerEndpoint[String, AuthenticatedContext, UUID, BackendException.AuthenticationError, String, Any, IO] = securedWithBearer
    .summary("Notify result of load processing")
    .description("TDR internal endpoint. Triggers post-processing functions")
    .post
    .in("load" / "process" / "result" / consignmentId)
    .out(jsonBody[String])

  val initiateLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(initiateLoadEndpoint.serverLogicSuccess(ac => _ => consignmentService.createConsignment(ac.token)))

  val completeLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(completeLoadEndpoint.serverLogicSuccess(ac => ci => dataLoadProcessing.trigger(ci, ac.token)))

  val processLoadResultRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(processLoadResultEndpoint.serverLogicSuccess(_ => ci => dataLoadResultsHandler.handleResult(ci)))
}

object LoadController {
  def apply() = new LoadController(ConsignmentService(), DataLoadProcessor(), DataLoadResultsHandler())
}
