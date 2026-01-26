package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.model.StatusCode
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.{Endpoint, EndpointInput, auth, endpoint, path, statusCode}
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation, AuthorisationContext, TokenAuthenticator}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException.{AuthenticationError, AuthorisationError}
import uk.gov.nationalarchives.tdr.transfer.service.api.interceptors.CustomInterceptors
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem

import java.util.UUID

trait BaseController {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  val sourceSystem: EndpointInput[SourceSystem] = path("sourceSystem")

  private val tokenAuthenticator = TokenAuthenticator()
  private val authorisation = Authorisation()

  private val securedWithBearerEndpoint: Endpoint[String, Unit, AuthenticationError, Unit, Any] = endpoint
    .securityIn(auth.bearer[String]())
    .errorOut(statusCode(StatusCode.Unauthorized))
    .errorOut(jsonBody[AuthenticationError])

  val transferId: EndpointInput[UUID] = path("transferId")

  val securedWithStandardUserBearer: PartialServerEndpoint[String, AuthenticatedContext, Unit, AuthenticationError, Unit, Any, IO] = securedWithBearerEndpoint
    .serverSecurityLogic(
      tokenAuthenticator.authenticateStandardUserToken
    )

  val validateUserHasAccessToConsignment: PartialServerEndpoint[(String, UUID), AuthorisationContext, Unit, AuthorisationError, Unit, Any, IO] = endpoint
    .securityIn(auth.bearer[String]().and(path[UUID]("transferId")))
    .errorOut(statusCode(StatusCode.Unauthorized))
    .errorOut(jsonBody[AuthorisationError])
    .serverSecurityLogic { case (bearer, transferId) =>
      authorisation.validateUserHasAccessToConsignment(bearer, transferId)
    }

  val customServerOptions: Http4sServerOptions[IO] = Http4sServerOptions
    .customiseInterceptors[IO]
    .corsInterceptor(CustomInterceptors.customCorsInterceptor)
    .options

  def routes: HttpRoutes[IO]
}
