package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.AuthenticatedContext
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{LoadDetails, TransferConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.{DataLoadConfiguration, DataLoadInitiation, DataLoadProcessor}

import java.util.UUID

class LoadController(dataLoadConfiguration: DataLoadConfiguration, dataLoadInitiation: DataLoadInitiation, dataLoadProcessor: DataLoadProcessor) extends BaseController {
  def endpoints: List[Endpoint[
    String,
    _ >: SourceSystem with (SourceSystem, UUID, Option[Boolean]) <: Serializable,
    BackendException.AuthenticationError,
    _ >: TransferConfiguration with LoadDetails with String <: Serializable,
    Any
  ]] =
    List(configurationEndpoint.endpoint, initiateLoadEndpoint.endpoint, completeLoadEndpoint.endpoint)

  override def routes: HttpRoutes[IO] = configurationRoute <+> initiateLoadRoute <+> completeLoadRoute

  private val configurationEndpoint: PartialServerEndpoint[String, AuthenticatedContext, SourceSystem, BackendException.AuthenticationError, TransferConfiguration, Any, IO] =
    securedWithBearer
      .summary("Configuration for client transfer")
      .description("Provides configuration for calling client before starting an operation")
      .post
      .in("load" / sourceSystem / "configuration")
      .out(jsonBody[TransferConfiguration])

  private val metadataOnly: EndpointInput[Option[Boolean]] = query("metadataOnly")

  private val initiateLoadEndpoint: PartialServerEndpoint[String, AuthenticatedContext, SourceSystem, BackendException.AuthenticationError, LoadDetails, Any, IO] =
    securedWithBearer
      .summary("Initiate the load of records and metadata")
      .post
      .in("load" / sourceSystem / "initiate")
      .out(jsonBody[LoadDetails])

  private val completeLoadEndpoint
      : PartialServerEndpoint[String, AuthenticatedContext, (SourceSystem, UUID, Option[Boolean]), BackendException.AuthenticationError, String, Any, IO] =
    securedWithBearer
      .summary("Notify that loading has completed")
      .description("Triggers the processing of the transfer's loaded metadata and records in TDR")
      .post
      .in("load" / sourceSystem / "complete" / transferId / metadataOnly)
      .out(jsonBody[String])

  val configurationRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(configurationEndpoint.serverLogicSuccess(_ => input => dataLoadConfiguration.configuration(input)))

  val initiateLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(initiateLoadEndpoint.serverLogicSuccess(ac => input => dataLoadInitiation.initiateConsignmentLoad(ac.token, input)))

  val completeLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(completeLoadEndpoint.serverLogicSuccess(ac => input => dataLoadProcessor.trigger(input._2, ac.token)))
}

object LoadController {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new LoadController(DataLoadConfiguration(), DataLoadInitiation(), DataLoadProcessor())
}
