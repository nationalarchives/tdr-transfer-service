package uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement

object UserGroups {
  sealed trait UserGroup {
    val name: String
  }

  sealed trait ParentTypeGroup extends UserGroup

  sealed trait UserTypeGroup extends UserGroup {
    val parentGroup: ParentTypeGroup
  }

  case object UserType extends ParentTypeGroup {
    val name = "user_type"
  }

  case object TnaUser extends UserTypeGroup with ParentTypeGroup {
    val name = "tna_user"
    val parentGroup: ParentTypeGroup = UserType
  }

  case object StandardUser extends UserTypeGroup {
    val name = "standard_user"
    val parentGroup: ParentTypeGroup = UserType
  }

  case object JudgmentUser extends UserTypeGroup {
    val name = "judgment_user"
    val parentGroup: ParentTypeGroup = UserType
  }

  case object TransferAdviser extends UserTypeGroup {
    val name = "transfer_adviser"
    val parentGroup: ParentTypeGroup = TnaUser
  }
}
