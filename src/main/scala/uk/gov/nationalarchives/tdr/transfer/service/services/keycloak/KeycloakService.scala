package uk.gov.nationalarchives.tdr.transfer.service.services.keycloak

import cats.effect.IO
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import sttp.client3.{Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class KeycloakService()(implicit backend: SttpBackend[Identity, Any]) {
  implicit class FutureUtils[T](f: Future[T]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }

  private val authConfig = ApplicationConfig.appConfig.auth
  private val authUrl = authConfig.url
  private val realm = authConfig.realm

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
    TdrKeycloakDeployment(s"$authUrl", realm, 8080)

  private val utils = KeycloakUtils()

  def userDetails(userId: UUID, clientId: String, clientSecret: String): IO[KeycloakUtils.UserDetails] = for {
    ud <- utils.userDetails(userId.toString, clientId, clientSecret).toIO
  } yield ud

  def accessToken(token: String): Either[Throwable, Token] = utils.token(token)

  def serviceAccountToken(clientId: String, clientSecret: String): IO[BearerAccessToken] = for {
    accToken <- utils.serviceAccountToken(clientId, clientSecret).toIO
  } yield accToken

}

object KeycloakService {
  def apply()(implicit backend: SttpBackend[Identity, Any]) = new KeycloakService
}
