package uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.{GroupRepresentation, UserRepresentation}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.KeycloakClient.SubGroupInput
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.UserGroups.UserGroup

import scala.jdk.CollectionConverters._

class KeycloakClient(realmResource: RealmResource) {
  def getGroup(group: UserGroup): GroupRepresentation = {
    realmResource
      .groups()
      .groups()
      .asScala
      .toList
      .find(_.getName == group.name)
      .getOrElse(throw new RuntimeException(s"No such group: ${group.name}"))
  }

  def getSubGroups(input: SubGroupInput): List[GroupRepresentation] = {
    realmResource
      .groups()
      .group(input.parentGroupId)
      .getSubGroups(input.first, input.max, input.basicRepresentation)
      .asScala
      .toList
  }

  def getGroupMembers(groupId: String, enabledOnly: Boolean): List[UserRepresentation] = {
    val allMembers = realmResource.groups().group(groupId).members().asScala.toList
    if (enabledOnly) {
      allMembers.filter(_.isEnabled)
    } else allMembers
  }
}

object KeycloakClient {
  private val keycloakConfig = ApplicationConfig.appConfig.keycloak
  private val tdrRealm = keycloakConfig.tdrRealm
  private val adminClient = KeycloakBuilder
    .builder()
    .serverUrl(keycloakConfig.url)
    .realm(tdrRealm)
    .clientId(keycloakConfig.adminClientId)
    .clientSecret(keycloakConfig.adminClientSecret)
    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
    .build()

  case class SubGroupInput(parentGroupId: String, totalSubGroups: Long, first: Int = 0, max: Int = 100, basicRepresentation: Boolean = true)

  def apply() = new KeycloakClient(adminClient.realm(tdrRealm))
}
