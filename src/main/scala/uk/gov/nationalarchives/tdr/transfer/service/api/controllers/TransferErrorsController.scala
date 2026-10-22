package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.PartialServerEndpoint
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.TransferErrorsController.TransferErrorsEndpointInput
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferErrorResultsModel.TransferErrorsResults
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors.TransferErrorsInput

import java.util.UUID

class TransferErrorsController(
    transferErrors: TransferErrors,
    authorisationModule: Authorisation
) extends BaseController {
  private val transferFunction: EndpointInput[TransferFunction] = path("transferFunction")

  override def routes: HttpRoutes[IO] = getErrorsRoute

  override val authorisation: Authorisation = authorisationModule

  def endpoints: List[Endpoint[String, TransferErrorsEndpointInput, BackendError, TransferErrorsResults, Any]] =
    List(getErrorsEndpoint.endpoint)

  private val getErrorsEndpoint: PartialServerEndpoint[String, AuthenticatedContext, TransferErrorsEndpointInput, BackendError, TransferErrorsResults, Any, IO] =
    securedWithStandardUserBearer
      .summary("Retrieve transfer errors for a given transfer")
      .description("Returns a list of transfer errors for the specified transfer ID")
      .in(s"${TransferFunction.Errors}" / transferFunction / transferId)
      .out(jsonBody[TransferErrorsResults])

  private def resultFunc(token: Token, input: TransferErrorsEndpointInput): IO[Either[BackendError, TransferErrorsResults]] = {
    val transferId = input._2
    val transferFunction = input._1
    lazy val prefix = s"$transferId/$transferFunction"
    transferErrors.result(TransferErrorsInput(token, transferId, prefix))
  }

  val getErrorsRoute: HttpRoutes[IO] =
    routesWithAuthorisation(getErrorsEndpoint, (ac: AuthenticatedContext, input: TransferErrorsEndpointInput) => authorisationFunc(ac.token, Some(input._2), None), resultFunc)
}

object TransferErrorsController {
  type TransferErrorsEndpointInput = (TransferFunction, UUID)

  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) =
    new TransferErrorsController(TransferErrors(), Authorisation())
}
