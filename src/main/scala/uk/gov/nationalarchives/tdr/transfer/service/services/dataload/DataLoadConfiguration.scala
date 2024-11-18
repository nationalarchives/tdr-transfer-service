package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.TransferConfiguration
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.transferConfigurationConfig

class DataLoadConfiguration {
  def configuration(sourceSystem: SourceSystem): IO[TransferConfiguration] = {
    val maxNumberRecords = transferConfigurationConfig.maxNumberRecords
    val maxIndividualFileSizeMb = transferConfigurationConfig.maxIndividualFileSizeMb
    val maxTransferSizeMb = transferConfigurationConfig.maxTransferSizeMb
    val metadataPropertyDetails = MetadataLoadConfiguration.metadataLoadConfiguration(sourceSystem)
    val disallowedFileExtensions = Set[String]()
    IO(TransferConfiguration(maxNumberRecords, maxIndividualFileSizeMb, maxTransferSizeMb, disallowedFileExtensions, metadataPropertyDetails))
  }
}

object DataLoadConfiguration {
  def apply() = new DataLoadConfiguration()
}
