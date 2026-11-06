package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir.{Endpoint, _}
import sttp.tapir.json.circe.jsonBody
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation.AuthorisationInput
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction.Email
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.UserNotifications
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.UserNotifications.EmailUserGroupInput

class UserManagementController(notifications: UserNotifications, authorisationModule: Authorisation) extends BaseController {
  def endpoints: List[Endpoint[String, EmailUserGroupInput, BackendError, Unit, Any]] =
    List(emailUserTypeGroupEndpoint.endpoint)

  override val authorisation: Authorisation = authorisationModule

  override def routes: HttpRoutes[IO] = emailUserTypeGroupRoute

  private val emailUserTypeGroupEndpoint =
    securedWithAdminUserBearer
      .summary("Email user type groups")
      .description("Send email to all enabled TDR users in group")
      .post
      .in(s"${TransferFunction.Admin}" / s"${TransferFunction.Email}" / "group" / "usertype")
      .in(jsonBody[EmailUserGroupInput])
      .out(jsonBody[Unit])

  private val emailUserTypeGroupRoute: HttpRoutes[IO] =
    routesWithAuthorisation(
      emailUserTypeGroupEndpoint,
      (ac: AuthenticatedContext, input: EmailUserGroupInput) => authorisationFunc(AuthorisationInput(ac.token, None, None, Email)),
      (token: Token, input: EmailUserGroupInput) => notifications.result(input)
    )
}

object UserManagementController {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new UserManagementController(UserNotifications(), Authorisation())
}
