package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation.AuthorisationInput
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.UserManagementController.EmailUserTypeEndpointInput
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction.Email
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.UserGroups.UserTypeGroup
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.{UserGroups, UserTypeGroupEnum}
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.UserNotifications
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.UserNotifications.EmailUserGroupInput

class UserManagementController(notifications: UserNotifications, authorisationModule: Authorisation) extends BaseController {
  def endpoints: List[Endpoint[String, _ >: (UserTypeGroupEnum.UserTypeGroup, EmailUserGroupInput) with Unit, BackendError, _ >: Unit with List[String], Any]] =
    List(emailUserTypeGroupEndpoint.endpoint, userTypeGroupsEndpoint.endpoint)

  override val authorisation: Authorisation = authorisationModule

  override def routes: HttpRoutes[IO] = emailUserTypeGroupRoute <+> userTypeGroupsRoute

  val userType: EndpointInput[UserTypeGroupEnum.UserTypeGroup] = path("usertype")

  private val userTypeGroupsEndpoint =
    securedWithAdminUserBearer
      .summary("Retrieve user type groups")
      .get
      .in(s"${TransferFunction.Admin}" / "groups" / "usertype")
      .out(jsonBody[List[String]])

  val userTypeGroupsRoute: HttpRoutes[IO] =
    routesWithAuthorisation(
      userTypeGroupsEndpoint,
      (ac: AuthenticatedContext, input: Unit) => authorisationFunc(AuthorisationInput(ac.token, None, None, TransferFunction.Admin)),
      (token: Token, input: Unit) => IO(Right(UserGroups.userTypeGroups.toList))
    )

  private val emailUserTypeGroupEndpoint =
    securedWithAdminUserBearer
      .summary("Email user type groups")
      .description("Send email to all enabled TDR users in group")
      .post
      .in(s"${TransferFunction.Admin}" / s"${TransferFunction.Email}" / "group" / userType)
      .in(jsonBody[EmailUserGroupInput])
      .out(jsonBody[Unit])

  private val emailUserTypeGroupRoute: HttpRoutes[IO] =
    routesWithAuthorisation(
      emailUserTypeGroupEndpoint,
      (ac: AuthenticatedContext, input: EmailUserTypeEndpointInput) => authorisationFunc(AuthorisationInput(ac.token, None, None, Email)),
      (token: Token, input: EmailUserTypeEndpointInput) => notifications.result(input._2)
    )
}

object UserManagementController {
  type EmailUserTypeEndpointInput = (UserTypeGroupEnum.UserTypeGroup, EmailUserGroupInput)
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new UserManagementController(UserNotifications(), Authorisation())
}
