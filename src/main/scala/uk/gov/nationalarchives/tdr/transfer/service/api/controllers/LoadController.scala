package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation.AuthorisationInput
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.{BackendError, ErrorHandler}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{LoadCompletion, LoadCompletionResponse, LoadDetails, TransferConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.DataLoadInitiationInput
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadProcessor.{DataLoadProcessorEvent, DataLoadProcessorInput}
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.{DataLoadConfiguration, DataLoadInitiation, DataLoadProcessor}

import java.util.UUID

class LoadController(
    dataLoadConfiguration: DataLoadConfiguration,
    dataLoadInitiation: DataLoadInitiation,
    dataLoadProcessor: DataLoadProcessor,
    authorisation: Authorisation
) extends BaseController {

  def endpoints: List[Endpoint[
    String,
    _ >: SourceSystem with (SourceSystem, Option[UUID]) with (SourceSystem, UUID, Option[Boolean], LoadCompletion) <: Serializable,
    BackendError,
    _ >: TransferConfiguration with LoadDetails with LoadCompletionResponse <: Product,
    Any
  ]] =
    List(configurationEndpoint.endpoint, initiateLoadEndpoint.endpoint, completeLoadEndpoint.endpoint)

  override def routes: HttpRoutes[IO] = configurationRoute <+> initiateLoadRoute <+> completeLoadRoute

  private val configurationEndpoint: PartialServerEndpoint[String, AuthenticatedContext, SourceSystem, BackendError, TransferConfiguration, Any, IO] =
    securedWithStandardUserBearer
      .summary("Configuration for client transfer")
      .description("Provides configuration for calling client before starting an operation")
      .get
      .in(s"${TransferFunction.Load}" / sourceSystem / "configuration")
      .out(jsonBody[TransferConfiguration])

  private val metadataOnly: EndpointInput[Option[Boolean]] = query("metadataOnly")
  private val existingTransferId: EndpointInput[Option[UUID]] = query("transferId")

  private val initiateLoadEndpoint: PartialServerEndpoint[String, AuthenticatedContext, (SourceSystem, Option[UUID]), BackendError, LoadDetails, Any, IO] =
    securedWithStandardUserBearer
      .summary("Initiate the load of records and metadata")
      .post
      .in(s"${TransferFunction.Load}" / sourceSystem / "initiate" / existingTransferId)
      .out(jsonBody[LoadDetails])

  private val completeLoadEndpoint: PartialServerEndpoint[
    String,
    AuthenticatedContext,
    (SourceSystem, UUID, Option[Boolean], LoadCompletion),
    BackendError,
    LoadCompletionResponse,
    Any,
    IO
  ] =
    securedWithStandardUserBearer
      .summary("Notify that loading has completed")
      .description("Triggers the processing of the transfer's loaded metadata and records in TDR")
      .post
      .in(s"${TransferFunction.Load}" / sourceSystem / "complete" / transferId / metadataOnly)
      .in(jsonBody[LoadCompletion])
      .out(jsonBody[LoadCompletionResponse])

  val configurationRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(configurationEndpoint.serverLogic { ac =>
      { sourceSystem =>
        val authorisationInput = AuthorisationInput(ac.token, None, Some(sourceSystem))
        val configurationIO = dataLoadConfiguration.result(sourceSystem)
        serviceLogicRunner(authorisationInput, authorisation, configurationIO)
      }
    })

  val initiateLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      initiateLoadEndpoint.serverLogic { ac =>
        { case (sourceSystem, existingTransferId) =>
          val authorisationInput = AuthorisationInput(ac.token, existingTransferId, Some(sourceSystem))
          val loadIO = dataLoadInitiation.result(DataLoadInitiationInput(ac.token, sourceSystem, existingTransferId))
          serviceLogicRunner(authorisationInput, authorisation, loadIO)
        }
      }
    )

  val completeLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      completeLoadEndpoint.serverLogic { ac =>
        { input =>
          val sourceSystem = input._1
          val authorisationInput = AuthorisationInput(ac.token, None, Some(sourceSystem))
          val processorIO = dataLoadProcessor.result(DataLoadProcessorInput(DataLoadProcessorEvent(sourceSystem, input._2, input._3, input._4), ac.token))
          serviceLogicRunner(authorisationInput, authorisation, processorIO)
        }
      }
    )
}

object LoadController {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]): LoadController = {
    new LoadController(DataLoadConfiguration(), DataLoadInitiation(), DataLoadProcessor(), Authorisation())
  }
}
