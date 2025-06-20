package uk.gov.nationalarchives.tdr.transfer.service.api.model

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.AutoDerivation
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import sttp.tapir.Schema
import sttp.tapir.generic.auto.SchemaDerivation
import sttp.tapir.generic.{Configuration => TapirConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.CustomMetadataModel.CustomPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.MetadataDataType.MetadataDataTypeEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.MetadataDataType.MetadataDataTypeEnum.MetadataDataType
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem

object Serializers extends AutoDerivation with SchemaDerivation {
  implicit val schemaConfiguration: TapirConfiguration = TapirConfiguration.default.withDiscriminator("type")

  implicit val sourceSystemEnc: Encoder[SourceSystem] = Encoder.encodeEnumeration(SourceSystemEnum)
  implicit val sourceSystemDec: Decoder[SourceSystem] = Decoder.decodeEnumeration(SourceSystemEnum)
  implicit val sourceSystemSch: Schema[SourceSystem] = Schema.derivedEnumerationValue[SourceSystem]

  implicit val metadataDataTypeEnc: Encoder[MetadataDataType] = Encoder.encodeEnumeration(MetadataDataTypeEnum)
  implicit val metadataDataTypeDec: Decoder[MetadataDataType] = Decoder.decodeEnumeration(MetadataDataTypeEnum)
  implicit val metadataDataTypeSch: Schema[MetadataDataType] = Schema.derivedEnumerationValue[MetadataDataType]
}
