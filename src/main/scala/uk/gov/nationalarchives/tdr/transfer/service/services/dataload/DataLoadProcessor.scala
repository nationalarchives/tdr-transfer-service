package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID

class DataLoadProcessor {
  def trigger(transferId: UUID, token: Token): IO[String] = {
    // Trigger data load processing
    IO("Data Load Processor: Stubbed Response")
  }
}

object DataLoadProcessor {
  def apply() = new DataLoadProcessor
}
