package uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.UserGroups.{JudgmentUser, SharePointUser, StandardUser}

object UserGroups {
  implicit val aEncoder: Encoder[UserGroups.UserTypeGroup] = deriveEncoder[UserGroups.UserTypeGroup]
  implicit val aDec: Decoder[UserGroups.UserTypeGroup] = deriveDecoder[UserGroups.UserTypeGroup]

  sealed trait UserGroup {
    val groupName: String
    val name: String
  }

  sealed trait ParentTypeGroup extends UserGroup

  sealed trait UserTypeGroup extends UserGroup {
    val parentGroup: ParentTypeGroup
  }

  case object UserType extends ParentTypeGroup {
    val groupName = "user_type"
    val name = "userType"
  }

  case object TnaUser extends UserTypeGroup with ParentTypeGroup {
    val groupName = "tna_user"
    val name = "tnaUser"
    val parentGroup: ParentTypeGroup = UserType
  }

  case object StandardUser extends UserTypeGroup {
    val groupName = "standard_user"
    val name = "standardUser"
    val parentGroup: ParentTypeGroup = UserType
  }

  case object JudgmentUser extends UserTypeGroup {
    val groupName = "judgment_user"
    val name = "judgmentUser"
    val parentGroup: ParentTypeGroup = UserType
  }

  case object SharePointUser extends UserTypeGroup {
    val groupName = "connector_for_sharepoint_user"
    val name = "sharepointConnectorUser"
    val parentGroup: ParentTypeGroup = UserType
  }

  case object TransferAdviser extends UserTypeGroup {
    val groupName = "transfer_adviser"
    val name = "transferAdviserUser"
    val parentGroup: ParentTypeGroup = TnaUser
  }

  def userTypeGroups: Set[String] = {
    Set(JudgmentUser.name, StandardUser.name, SharePointUser.name)
  }
}

object UserTypeGroupEnum extends Enumeration {
  type UserTypeGroup = Value
  val JudgmentUserType: Value = Value(JudgmentUser.name)
  val SharePointUserTYpe: Value = Value(SharePointUser.name)
  val StandardUserType: Value = Value(StandardUser.name)
}
