package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation.AuthorisationInput
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation, TokenAuthenticator}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors._
import uk.gov.nationalarchives.tdr.transfer.service.api.interceptors.CustomInterceptors
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem

import java.util.UUID

trait BaseController {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  val errorHandler = new ErrorHandler()
  val authorisation: Authorisation

  val sourceSystem: EndpointInput[SourceSystem] = path("sourceSystem")
  val transferId: EndpointInput[UUID] = path("transferId")

  private val tokenAuthenticator = TokenAuthenticator()

  private val baseEndpoint = endpoint
    .errorOut(
      oneOf[BackendError](
        oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[AuthenticationError].description("User not authenticated"))),
        oneOfVariant(statusCode(StatusCode.Forbidden).and(jsonBody[AuthorisationError].description("User not authorised"))),
        oneOfVariant(statusCode(StatusCode.Forbidden).and(jsonBody[SeriesAssignmentError].description("Too many unassigned transfers"))),
        oneOfVariant(statusCode(StatusCode.Forbidden).and(jsonBody[TransferStateError].description("Transfer in incorrect state for upload"))),
        oneOfDefaultVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[BackendError].description("Unknown")))
      )
    )
    .out(header("X-Content-Type-Options", "nosniff"))
    .out(header("Strict-Transport-Security", "max-age=31536000; includeSubDomains"))
    .out(header("X-Frame-Options", "DENY"))
    .out(header("Referrer-Policy", "origin"))
    .out(
      header(
        "Content-Security-Policy",
        "child-src 'self'; default-src 'self'; base-uri 'none'; script-src 'strict-dynamic'; connect-src 'self'; object-src 'none'"
      )
    )

  private val securedWithBearerEndpoint = baseEndpoint
    .securityIn(auth.bearer[String]())

  val securedWithStandardUserBearer: PartialServerEndpoint[String, AuthenticatedContext, Unit, BackendError, Unit, Any, IO] = securedWithBearerEndpoint
    .serverSecurityLogic(
      tokenAuthenticator.authenticateStandardUserToken
    )

  private val customServerOptions: Http4sServerOptions[IO] = Http4sServerOptions
    .customiseInterceptors[IO]
    .corsInterceptor(CustomInterceptors.customCorsInterceptor)
    .options

  def routes: HttpRoutes[IO]

  def authorisationFunc(token: Token, transferId: Option[UUID], sourceSystem: Option[SourceSystem]): IO[Unit] = {
    val authorisationInput = AuthorisationInput(token, transferId, sourceSystem)
    authorisation.endpointAuthorisation(authorisationInput)
  }

  def routesWithAuthorisation[IT, RT](
      endpoint: PartialServerEndpoint[String, AuthenticatedContext, IT, BackendError, RT, Any, IO],
      authorisationIOFunc: (AuthenticatedContext, IT) => IO[Unit],
      resultIOFunc: (Token, IT) => IO[Either[BackendError, RT]]
  ): HttpRoutes[IO] = {
    Http4sServerInterpreter[IO](customServerOptions)
      .toRoutes(endpoint.serverLogic { ac => input: IT =>
        (for {
          _ <- authorisationIOFunc(ac, input)
          result <- resultIOFunc(ac.token, input)
        } yield result).handleErrorWith {
          case err: BackendError =>
            errorHandler.handleErrorAsLeft(err)
          case err =>
            IO.raiseError(err)
        }
      })
  }
}
