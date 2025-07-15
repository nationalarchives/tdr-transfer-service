package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.AuthenticatedContext
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferState.StateTypeEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferState.StateValueEnum.StateValue
import uk.gov.nationalarchives.tdr.transfer.service.services.transfermanagement.TransferManagement
import uk.gov.nationalarchives.tdr.transfer.service.services.transfermanagement.TransferManagement.TransferState

import java.util.UUID

class TransferManagementController(transferManagement: TransferManagement) extends BaseController {
  private val stateValue: EndpointInput[StateValue] = path("stateValue")

  override def routes: HttpRoutes[IO] = uploadProcessingRoute

  def endpoints: Seq[Endpoint[String, (SourceSystem, StateValue, UUID), BackendException.AuthenticationError, String, Any]] = List(uploadProcessingEndpoint.endpoint)

  private val uploadProcessingEndpoint
      : PartialServerEndpoint[String, AuthenticatedContext, (SourceSystem, StateValue, UUID), BackendException.AuthenticationError, String, Any, IO] =
    securedWithTransferServiceClientBearer
      .summary("Management of transfer on completed data load")
      .description("Triggers processing of completed data load")
      .post
      .in("processing" / sourceSystem / "upload" / stateValue / transferId)
      .out(jsonBody[String])

  val uploadProcessingRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      uploadProcessingEndpoint.serverLogicSuccess(ac => input => transferManagement.uploadProcessing(ac.token, input._1, input._3, TransferState(StateTypeEnum.Upload, input._2)))
    )
}

object TransferManagementController {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TransferManagementController(TransferManagement())
}
