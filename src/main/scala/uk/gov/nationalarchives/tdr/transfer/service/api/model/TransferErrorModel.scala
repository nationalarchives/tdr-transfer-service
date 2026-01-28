package uk.gov.nationalarchives.tdr.transfer.service.api.model

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, parser}
import sttp.tapir.Schema

import java.util.UUID
import scala.util.Try

object TransferErrorModel {
  case class TransferErrorsResults(uploadCompleted: Boolean, errors: List[Json], transferId: UUID)

  object TransferErrorsResults {
    // Circe encoders/decoders to allow Tapir to derive json codecs
    implicit val uuidEncoder: Encoder[UUID] = Encoder.encodeString.contramap[UUID](_.toString)
    implicit val uuidDecoder: Decoder[UUID] = Decoder.decodeString.emap { s =>
      try Right(UUID.fromString(s))
      catch { case _: IllegalArgumentException => Left("Invalid UUID") }
    }

    // Provide Tapir Schema instances required for derivation
    implicit val uuidSchema: Schema[UUID] =
      Schema.string.map[UUID]((s: String) => Try(UUID.fromString(s)).toOption)((u: UUID) => u.toString)

    implicit val jsonSchema: Schema[Json] =
      Schema.string.map[Json]((s: String) => parser.parse(s).toOption)((j: Json) => j.noSpaces)

    implicit val transferErrorsResultsEncoder: Encoder[TransferErrorsResults] = deriveEncoder
    implicit val transferErrorsResultsDecoder: Decoder[TransferErrorsResults] = deriveDecoder

    // Tapir Schema derived from the case class so endpoints can use jsonBody[TransferErrorsResults]
    implicit val transferErrorsResultsSchema: Schema[TransferErrorsResults] = Schema.derived[TransferErrorsResults]
  }
}
