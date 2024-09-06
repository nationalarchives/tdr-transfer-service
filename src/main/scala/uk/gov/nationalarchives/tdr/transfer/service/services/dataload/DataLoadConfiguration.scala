package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.TransferConfiguration
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.transferConfigurationConfig

class DataLoadConfiguration {
  def configuration(sourceSystem: SourceSystem): IO[TransferConfiguration] = {
    val maxNumberRecords = transferConfigurationConfig.maxNumberRecords
    val metadataProperties = MetadataLoadConfiguration.metadataLoadConfiguration(sourceSystem)
    IO(TransferConfiguration(maxNumberRecords, metadataProperties))
  }
}

object DataLoadConfiguration {
  val transferConfigurationConfig: ApplicationConfig.TransferConfiguration = ApplicationConfig.appConfig.transferConfiguration
  def apply() = new DataLoadConfiguration()
}
