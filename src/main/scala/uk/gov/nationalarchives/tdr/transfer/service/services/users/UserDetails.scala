package uk.gov.nationalarchives.tdr.transfer.service.services.users

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.resource.{RealmResource, UsersResource}
import org.keycloak.admin.client.{Keycloak, KeycloakBuilder}
import org.keycloak.representations.idm.UserRepresentation
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig

class UserDetails(client: Keycloak, authConfig: ApplicationConfig.Auth)(implicit
    backend: SttpBackend[Identity, Any]
) {
  private def realmResource(client: Keycloak): RealmResource = client.realm(authConfig.realm)
  private def usersResource(realm: RealmResource): UsersResource = realm.users()

  def getUserRepresentation(userId: String): UserRepresentation = {
    val realm = realmResource(client)
    val users = usersResource(realm)

    try {
      val userResource = users.get(userId)
      userResource.toRepresentation
    } catch {
      case e: Exception => throw new RuntimeException(s"No valid user found $userId: ${e.getMessage}")
    }
  }

}

object UserDetails {
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  private val authConfig: ApplicationConfig.Auth = ApplicationConfig.appConfig.auth

  def apply()(implicit backend: SttpBackend[Identity, Any]): UserDetails = {
    val client = KeycloakBuilder
      .builder()
      .serverUrl(authConfig.url)
      .realm(authConfig.realm)
      .clientId(authConfig.userReadClientId)
      .clientSecret(authConfig.userReadClientSecret)
      .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
      .build()
    new UserDetails(client, authConfig)
  }
}
