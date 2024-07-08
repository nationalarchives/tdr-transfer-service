package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import sttp.model.StatusCode
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.{Endpoint, auth, endpoint, statusCode}
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, TokenAuthenticator}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException.AuthenticationError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._

trait BaseController {

  private val tokenAuthenticator = TokenAuthenticator()

  private val securedWithBearerEndpoint: Endpoint[String, Unit, AuthenticationError, Unit, Any] = endpoint
    .securityIn(auth.bearer[String]())
    .errorOut(statusCode(StatusCode.Unauthorized))
    .errorOut(jsonBody[AuthenticationError])

  val securedWithBearer: PartialServerEndpoint[String, AuthenticatedContext, Unit, AuthenticationError, Unit, Any, IO] = securedWithBearerEndpoint
    .serverSecurityLogic(
      tokenAuthenticator.authenticateUserToken
    )
}
