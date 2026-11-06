package uk.gov.nationalarchives.tdr.transfer.service.services.notifications

import cats.effect.IO
import org.keycloak.representations.idm.UserRepresentation
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError
import uk.gov.nationalarchives.tdr.transfer.service.services.BaseService
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.KeycloakClient
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.KeycloakClient.SubGroupInput
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.UserGroups.UserTypeGroup
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.EmailClient.SendEmailInput
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.UserNotifications.{EmailUserGroupInput, NotificationsInput}

import java.util.UUID

class UserNotifications(keycloakClient: KeycloakClient, emailClient: EmailClient) extends BaseService[NotificationsInput, Unit] {
  private def getUsersByGroup(userTypeGroup: UserTypeGroup): List[UserRepresentation] = {
    val parentGroup = keycloakClient.getGroup(userTypeGroup.parentGroup)
    val subGroups = keycloakClient.getSubGroups(
      SubGroupInput(parentGroup.getId, totalSubGroups = parentGroup.getSubGroupCount)
    )
    val subGroup = subGroups
      .find(g => g.getName.contains(userTypeGroup.name))
      .getOrElse(throw new RuntimeException(s"No such user group: ${userTypeGroup.name}"))
    keycloakClient.getGroupMembers(subGroup.getId, enabledOnly = true)
  }

  private def sendUserGroupEmail(input: EmailUserGroupInput) = {
    val groupUsers = getUsersByGroup(input.userType)
    val emailInputs =
      groupUsers.map(u => SendEmailInput(emailAddress = u.getEmail, personalisation = Map("generic_message" -> input.message), reference = UUID.randomUUID().toString))
    IO(Right(emailClient.sendEmail(emailInputs)))
  }

  def result(input: NotificationsInput): IO[Either[BackendError, Unit]] = {
    input match {
      case EmailUserGroupInput(_, _) => sendUserGroupEmail(input.asInstanceOf[EmailUserGroupInput])
      case _                         => throw new RuntimeException("Unrecognised input")
    }
  }
}

object UserNotifications {
  trait NotificationsInput
  case class EmailUserGroupInput(userType: UserTypeGroup, message: String) extends NotificationsInput

  def apply() = new UserNotifications(KeycloakClient(), EmailClient())
}
