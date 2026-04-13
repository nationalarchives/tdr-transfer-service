package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import uk.gov.nationalarchives.tdr.schema.generated.ExcludedFilenames
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{Header, TransferConfiguration}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.transferConfigurationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.s3Config

class DataLoadConfiguration {
  private val s3PutRequestHeaders: Set[Header] = Set(
    Header("ACL", s"${s3Config.aclHeaderValue}"),
    Header("If-None-Match", s"${s3Config.ifNoneMatchHeaderValue}")
  )

  def configuration(sourceSystem: SourceSystem): IO[TransferConfiguration] = {
    val maxNumberRecords = transferConfigurationConfig.maxNumberRecords
    val maxIndividualFileSizeMb = transferConfigurationConfig.maxIndividualFileSizeMb
    val maxTransferSizeMb = transferConfigurationConfig.maxTransferSizeMb
    val metadataPropertyDetails = MetadataLoadConfiguration.metadataLoadConfiguration(sourceSystem)
    val disallowedFileExtensions = Set[String]()
    val disallowedFileNames = ExcludedFilenames.all.toSet
    IO(
      TransferConfiguration(
        maxNumberRecords,
        maxIndividualFileSizeMb,
        maxTransferSizeMb,
        disallowedFileExtensions,
        disallowedFileNames,
        metadataPropertyDetails,
        s3PutRequestHeaders = s3PutRequestHeaders
      )
    )
  }
}

object DataLoadConfiguration {
  def apply() = new DataLoadConfiguration()
}
