package uk.gov.nationalarchives.tdr.transfer.service.api.model

import io.circe.generic.AutoDerivation
import sttp.tapir.generic.auto.SchemaDerivation
import sttp.tapir.generic.{Configuration => TapirConfiguration}

object Serializers extends AutoDerivation with SchemaDerivation {
  implicit val schemaConfiguration: TapirConfiguration = TapirConfiguration.default.withDiscriminator("type")
}
