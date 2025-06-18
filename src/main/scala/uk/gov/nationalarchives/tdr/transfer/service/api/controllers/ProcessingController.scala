package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.interceptors.CustomInterceptors
import uk.gov.nationalarchives.tdr.transfer.service.api.model.ProcessingState.ProcessingStateEnum.ProcessingState
import uk.gov.nationalarchives.tdr.transfer.service.api.model.ProcessingType.ProcessingTypeEnum.ProcessingType
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem

import java.util.UUID

class ProcessingController() extends BaseController {
  private val customServerOptions: Http4sServerOptions[IO] = Http4sServerOptions
    .customiseInterceptors[IO]
    .corsInterceptor(CustomInterceptors.customCorsInterceptor)
    .options

  def endpoints: List[Endpoint[String, (SourceSystem, ProcessingType, ProcessingState, UUID), BackendException.AuthenticationError, Unit, Any]] =
    List(processingCompleteEndpoint.endpoint)

  override def routes: HttpRoutes[IO] = processingCompleteRoute

  private val processingCompleteEndpoint =
    securedWithTransferServiceRoleBearer
      .summary("Triggers relevant processing")
      .description("Depending on the provided type and state will start the necessary processing of the transfer")
      .post
      .in("processing" / sourceSystem / processingType / processingState / transferId)


  private val processingCompleteRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(processingCompleteEndpoint.serverLogicSuccess(_ => input => IO.unit))
}

object ProcessingController {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new ProcessingController()
}
