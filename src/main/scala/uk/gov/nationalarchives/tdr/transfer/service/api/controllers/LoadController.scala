package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.LoadController.{CompleteEndpointInput, ConfigurationEndpointInput, InitiateEndpointInput}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction.Load
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
    authorisationModule: Authorisation
) extends BaseController {
  def endpoints: List[Endpoint[
    String,
    _ >: ConfigurationEndpointInput with InitiateEndpointInput with CompleteEndpointInput <: Serializable,
    BackendError,
    _ >: TransferConfiguration with LoadDetails with LoadCompletionResponse <: Product,
    Any
  ]] = List(configurationEndpoint.endpoint, initiateLoadEndpoint.endpoint, completeLoadEndpoint.endpoint)

  override def routes: HttpRoutes[IO] = configurationRoute <+> initiateLoadRoute <+> completeLoadRoute

  override val authorisation: Authorisation = authorisationModule

  private val configurationEndpoint: PartialServerEndpoint[String, AuthenticatedContext, ConfigurationEndpointInput, BackendError, TransferConfiguration, Any, IO] =
    securedWithStandardUserBearer
      .summary("Configuration for client transfer")
      .description("Provides configuration for calling client before starting an operation")
      .get
      .in(s"${TransferFunction.Load}" / sourceSystem / "configuration")
      .out(jsonBody[TransferConfiguration])

  private val metadataOnly: EndpointInput[Option[Boolean]] = query("metadataOnly")
  private val existingTransferId: EndpointInput[Option[UUID]] = query("transferId")

  private val initiateLoadEndpoint: PartialServerEndpoint[String, AuthenticatedContext, InitiateEndpointInput, BackendError, LoadDetails, Any, IO] =
    securedWithStandardUserBearer
      .summary("Initiate the load of records and metadata")
      .post
      .in(s"${TransferFunction.Load}" / sourceSystem / "initiate" / existingTransferId)
      .out(jsonBody[LoadDetails])

  private val completeLoadEndpoint: PartialServerEndpoint[
    String,
    AuthenticatedContext,
    CompleteEndpointInput,
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
    routesWithAuthorisation(
      configurationEndpoint,
      (ac: AuthenticatedContext, input: ConfigurationEndpointInput) => authorisationFunc(ac.token, None, Some(input), Load),
      (_, input: ConfigurationEndpointInput) => dataLoadConfiguration.result(input)
    )

  val initiateLoadRoute: HttpRoutes[IO] =
    routesWithAuthorisation(
      initiateLoadEndpoint,
      (ac: AuthenticatedContext, input: InitiateEndpointInput) => authorisationFunc(ac.token, input._2, Some(input._1), Load),
      (token: Token, input: InitiateEndpointInput) => dataLoadInitiation.result(DataLoadInitiationInput(token, input._1, input._2))
    )

  val completeLoadRoute: HttpRoutes[IO] =
    routesWithAuthorisation(
      completeLoadEndpoint,
      (ac: AuthenticatedContext, input: CompleteEndpointInput) => authorisationFunc(ac.token, Some(input._2), Some(input._1), Load),
      (token: Token, input: CompleteEndpointInput) => dataLoadProcessor.result(DataLoadProcessorInput(DataLoadProcessorEvent(input._1, input._2, input._3, input._4), token))
    )
}

object LoadController {
  type CompleteEndpointInput = (SourceSystem, UUID, Option[Boolean], LoadCompletion)
  type ConfigurationEndpointInput = SourceSystem
  type InitiateEndpointInput = (SourceSystem, Option[UUID])

  def apply()(implicit logger: SelfAwareStructuredLogger[IO]): LoadController = {
    new LoadController(DataLoadConfiguration(), DataLoadInitiation(), DataLoadProcessor(), Authorisation())
  }
}
