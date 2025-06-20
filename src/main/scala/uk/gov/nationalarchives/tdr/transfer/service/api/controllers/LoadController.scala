package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.AuthenticatedContext
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.interceptors.CustomInterceptors
import uk.gov.nationalarchives.tdr.transfer.service.api.model.CustomMetadataModel.CustomPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{LoadCompletion, LoadDetails, TransferConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.{DataLoadConfiguration, DataLoadInitiation, DataLoadProcessor}

import java.util.UUID

class LoadController(dataLoadConfiguration: DataLoadConfiguration, dataLoadInitiation: DataLoadInitiation, dataLoadProcessor: DataLoadProcessor) extends BaseController {
  private val customServerOptions: Http4sServerOptions[IO] = Http4sServerOptions
    .customiseInterceptors[IO]
    .corsInterceptor(CustomInterceptors.customCorsInterceptor)
    .options

  def endpoints: List[Endpoint[
    String,
    _ >: SourceSystem with (SourceSystem, UUID, Option[Boolean], LoadCompletion) with (SourceSystem, UUID, Set[CustomPropertyDetails]) <: Serializable,
    BackendException.AuthenticationError,
    _ >: TransferConfiguration with LoadDetails with String <: Serializable,
    Any
  ]] =
    List(configurationEndpoint.endpoint, initiateLoadEndpoint.endpoint, completeLoadEndpoint.endpoint, customMetadataDetailsEndpoint.endpoint)

  override def routes: HttpRoutes[IO] = configurationRoute <+> initiateLoadRoute <+> completeLoadRoute <+> customMetadataDetailsRoute

  private val configurationEndpoint: PartialServerEndpoint[String, AuthenticatedContext, SourceSystem, BackendException.AuthenticationError, TransferConfiguration, Any, IO] =
    securedWithStandardUserBearer
      .summary("Configuration for client transfer")
      .description("Provides configuration for calling client before starting an operation")
      .get
      .in("load" / sourceSystem / "configuration")
      .out(jsonBody[TransferConfiguration])

  private val metadataOnly: EndpointInput[Option[Boolean]] = query("metadataOnly")

  private val initiateLoadEndpoint: PartialServerEndpoint[String, AuthenticatedContext, SourceSystem, BackendException.AuthenticationError, LoadDetails, Any, IO] =
    securedWithStandardUserBearer
      .summary("Initiate the load of records and metadata")
      .post
      .in("load" / sourceSystem / "initiate")
      .out(jsonBody[LoadDetails])

  private val completeLoadEndpoint
      : PartialServerEndpoint[String, AuthenticatedContext, (SourceSystem, UUID, Option[Boolean], LoadCompletion), BackendException.AuthenticationError, String, Any, IO] =
    securedWithStandardUserBearer
      .summary("Notify that loading has completed")
      .description("Triggers the processing of the transfer's loaded metadata and records in TDR")
      .post
      .in("load" / sourceSystem / "complete" / transferId / metadataOnly)
      .in(jsonBody[LoadCompletion])
      .out(jsonBody[String])

  private val customMetadataDetailsEndpoint
      : PartialServerEndpoint[String, AuthenticatedContext, (SourceSystem, UUID, Set[CustomPropertyDetails]), BackendException.AuthenticationError, String, Any, IO] =
    securedWithStandardUserBearer
      .summary("Details of any custom metadata properties for the transfer")
      .description("Provide details of custom metadata properties that are to be included as part of the transfer")
      .post
      .in("load" / sourceSystem / "custom-metadata" / "details" / transferId)
      .in(jsonBody[Set[CustomPropertyDetails]])
      .out(jsonBody[String])

  val configurationRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(configurationEndpoint.serverLogicSuccess(_ => input => dataLoadConfiguration.configuration(input)))

  val initiateLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(initiateLoadEndpoint.serverLogicSuccess(ac => input => dataLoadInitiation.initiateConsignmentLoad(ac.token, input)))

  val completeLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(completeLoadEndpoint.serverLogicSuccess(ac => input => dataLoadProcessor.trigger(input._2, ac.token)))

  val customMetadataDetailsRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      customMetadataDetailsEndpoint.serverLogicSuccess(ac => input => dataLoadProcessor.customMetadataDetails(input._1, input._2, ac.token, input._3))
    )
}

object LoadController {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new LoadController(DataLoadConfiguration(), DataLoadInitiation(), DataLoadProcessor())
}
