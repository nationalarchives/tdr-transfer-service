package uk.gov.nationalarchives.tdr.transfer.service.services

import sttp.client3.{Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig

import java.util.UUID
import scala.concurrent.ExecutionContext

class KeycloakService()(implicit backend: SttpBackend[Identity, Any]) {
  private val appConfig = ApplicationConfig.appConfig

  private val authUrl = appConfig.auth.url
  private val realm = appConfig.auth.realm

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
    TdrKeycloakDeployment(s"$authUrl", realm, 8080)

  def userDetails(userId: UUID, clientId: String, clientSecret: String) = for {
    ud <- KeycloakUtils().userDetails(userId.toString, clientId, clientSecret)
  } yield ud


}

object KeycloakService {
  def apply()(implicit backend: SttpBackend[Identity, Any]) = new KeycloakService
}
