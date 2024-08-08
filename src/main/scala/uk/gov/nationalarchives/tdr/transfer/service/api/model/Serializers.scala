package uk.gov.nationalarchives.tdr.transfer.service.api.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.AutoDerivation
import sttp.tapir.Schema
import sttp.tapir.generic.auto.SchemaDerivation
import sttp.tapir.generic.{Configuration => TapirConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem

object Serializers extends AutoDerivation with SchemaDerivation {
  implicit val schemaConfiguration: TapirConfiguration = TapirConfiguration.default.withDiscriminator("type")

  implicit val sourceSystemEnc: Encoder[SourceSystem] = Encoder.encodeEnumeration(SourceSystemEnum)
  implicit val genderDec: Decoder[SourceSystem] = Decoder.decodeEnumeration(SourceSystemEnum)
  implicit val genderSch: Schema[SourceSystem] = Schema.derivedEnumerationValue[SourceSystem]
}
