package uk.gov.nationalarchives.tdr.transfer.service.api.model

import io.circe.generic.AutoDerivation
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import sttp.tapir.generic.auto.SchemaDerivation
import sttp.tapir.generic.{Configuration => TapirConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.UserTypeGroupEnum
import uk.gov.nationalarchives.tdr.transfer.service.services.identitymanagement.UserTypeGroupEnum.UserTypeGroup

object Serializers extends AutoDerivation with SchemaDerivation {
  implicit val schemaConfiguration: TapirConfiguration = TapirConfiguration.default.withDiscriminator("type")

  implicit val sourceSystemEnc: Encoder[SourceSystem] = Encoder.encodeEnumeration(SourceSystemEnum)
  implicit val sourceSystemDec: Decoder[SourceSystem] = Decoder.decodeEnumeration(SourceSystemEnum)
  implicit val sourceSystemSch: Schema[SourceSystem] = Schema.derivedEnumerationValue[SourceSystem]

  implicit val userTypeGroupEnc: Encoder[UserTypeGroup] = Encoder.encodeEnumeration(UserTypeGroupEnum)
  implicit val userTypeGroupDec: Decoder[UserTypeGroup] = Decoder.decodeEnumeration(UserTypeGroupEnum)
  implicit val userTypeGroupSch: Schema[UserTypeGroup] = Schema.derivedEnumerationValue[UserTypeGroup]
}
