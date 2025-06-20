package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.model.CustomMetadataModel.CustomPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem

import java.util.UUID

class DataLoadProcessor {
  def trigger(transferId: UUID, token: Token): IO[String] = {
    // Trigger data load processing
    IO("Data Load Processor: Stubbed Response")
  }

  def customMetadataDetails(sourceSystem: SourceSystem, transferId: UUID, token: Token, customMetadataDetails: Set[CustomPropertyDetails]): IO[String] = {
    // Put Json to S3 bucket
    IO("Custom Metadata Details: Stubbed Response")
  }
}

object DataLoadProcessor {
  def apply() = new DataLoadProcessor
}
