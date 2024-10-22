package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.model.StatusCode
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.{Endpoint, EndpointInput, auth, endpoint, path, statusCode}
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, TokenAuthenticator}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException.AuthenticationError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem

import java.util.UUID

trait BaseController {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  val sourceSystem: EndpointInput[SourceSystem] = path("sourceSystem")

  private val tokenAuthenticator = TokenAuthenticator()

  private val securedWithBearerEndpoint: Endpoint[String, Unit, AuthenticationError, Unit, Any] = endpoint
    .securityIn(auth.bearer[String]())
    .errorOut(statusCode(StatusCode.Unauthorized))
    .errorOut(jsonBody[AuthenticationError])

  val transferId: EndpointInput[UUID] = path("transferId")

  val securedWithBearer: PartialServerEndpoint[String, AuthenticatedContext, Unit, AuthenticationError, Unit, Any, IO] = securedWithBearerEndpoint
    .serverSecurityLogic(
      tokenAuthenticator.authenticateUserToken
    )

  def routes: HttpRoutes[IO]
}
