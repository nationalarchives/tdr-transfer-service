package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir.{stringBody, _}
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.ErrorController.ErrorResponse
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import io.circe.generic.auto._
import sttp.tapir.server.PartialServerEndpoint
import uk.gov.nationalarchives.aws.utils.s3.{S3Clients, S3Utils}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.AuthenticatedContext
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.RetrieveErrors

import java.util.UUID

class ErrorController(retrieveErrors: RetrieveErrors)(implicit logger: SelfAwareStructuredLogger[IO]) extends BaseController {
  import io.circe._
  implicit val errorResponseEncoder: io.circe.Encoder[ErrorResponse] = io.circe.generic.semiauto.deriveEncoder
  implicit val errorResponseDecoder: Decoder[ErrorResponse] = io.circe.generic.semiauto.deriveDecoder

  private val existingTransferId: EndpointInput[Option[UUID]] = query("transferId")

  def endpoints: List[Endpoint[String, (SourceSystem, Option[UUID]), BackendException.AuthenticationError, List[Json], Any]] =
    List(secureErrorEndpoint2.endpoint)
  /*
Security input type — String
Comes from .securityIn(auth.bearer[String]()) (the bearer token).

Request input type — (SourceSystem, UUID)
Comes from the path .in("load" / sourceSystem / "errors" / transferId); the endpoint receives a SourceSystem and a UUID (the transfer id).

Error output type — ErrorResponse
Produced by .errorOut(jsonBody[ErrorResponse]); used when authentication or business logic returns a failure (Left).

Success output type — String
Produced by .out(stringBody); used when business logic returns success (Right).

Effect/streaming placeholder — Any
A generic type parameter for effect/streaming; commonly left as Any for effect-agnostic endpoint definitions.
   */
  val secureErrorEndpoint: Endpoint[String, (SourceSystem, Option[UUID]), ErrorResponse, String, Any] =
    endpoint.get
      .securityIn(auth.bearer[String]())
      .in("load" / sourceSystem / "errors" / existingTransferId)
      .errorOut(jsonBody[ErrorResponse])
      .out(stringBody)

  /*
  SecurityIn (String)
the raw security input type (here a bearer token string).
SecuredContext (AuthenticatedContext)
the authenticated principal/context produced by the security logic and passed to handlers.
RequestIn ((SourceSystem, UUID))
the request input captured from the endpoint path/body — here a tuple of SourceSystem and UUID (transferId).
ErrorOut (BackendException.AuthenticationError)
the error type returned on failures (authentication/business errors) for this partial endpoint.
SuccessOut (String)
the success response type (here returned as stringBody).
Streams (Any)
placeholder for streaming/effect-agnostic endpoints (common to leave as Any).
Effect (IO)
the effect type the server logic runs in (Cats-Effect IO).
   */
  import sttp.tapir.json.circe._
  val secureErrorEndpoint2: PartialServerEndpoint[String, AuthenticatedContext, (SourceSystem, Option[UUID]), BackendException.AuthenticationError, List[Json], Any, IO] =
    securedWithStandardUserBearer
      .summary("Triggers relevant processing")
      .description("Depending on the provided type and state will start the necessary processing of the transfer")
      .in("load" / sourceSystem / "errors" / existingTransferId)
      .out(jsonBody[List[Json]])

  // 2. Security logic
  private def authLogic(token: String): IO[Either[ErrorResponse, Unit]] =
    IO.pure(Right(()))

  // 3. Combine security and business logic
//  override def routes: HttpRoutes[IO] =
//    Http4sServerInterpreter[IO]().toRoutes(
//      secureErrorEndpoint
//        .serverSecurityLogic(authLogic)
//        .serverLogic { _ => _ => IO.pure(Right("Hello, world")) }
//    )
  override def routes: HttpRoutes[IO] = getErrorsRoute

  private val getErrorsRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO](customServerOptions).toRoutes(
      secureErrorEndpoint2.serverLogicSuccess(ac => input => retrieveErrors.getErrorsFromS3(ac.token, input._1, input._2))
    )
}

object ErrorController {
  case class ErrorResponse(
      transferId: UUID,
      matchId: Option[UUID],
      source: Option[String],
      errorId: UUID,
      errorCode: String,
      errorMessage: String
  )

  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new ErrorController(RetrieveErrors())
}
