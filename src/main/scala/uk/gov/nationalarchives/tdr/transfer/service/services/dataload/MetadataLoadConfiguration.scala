package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.schema.SchemaHandler

import scala.jdk.CollectionConverters._

object MetadataLoadConfiguration {
  private val schemaConfig: ApplicationConfig.Schema = ApplicationConfig.appConfig.schema

  private def sourceSystemSchemaMapping(sourceSystem: SourceSystem): String = sourceSystem match {
    case SourceSystemEnum.SharePoint   => schemaConfig.dataLoadSharePointLocation
    case SourceSystemEnum.HardDrive    => schemaConfig.hardDriveLocation
    case SourceSystemEnum.NetworkDrive => schemaConfig.networkDriveLocation
    case _                             => throw new RuntimeException(s"Source System '$sourceSystem' not mapped to schema")
  }

  def metadataLoadConfiguration(sourceSystem: SourceSystem): Set[MetadataPropertyDetails] = {
    if (sourceSystem == SourceSystemEnum.HardDrive || sourceSystem == SourceSystemEnum.NetworkDrive) {
      Set()
    } else {
      val schemaLocation = sourceSystemSchemaMapping(sourceSystem)
      val schema = SchemaHandler.schema(schemaLocation)
      val properties = schema.get("properties").properties().asScala
      val requiredProperties = schema.get("required").asScala.map(_.asText()).toSet
      properties
        .map(p => {
          MetadataPropertyDetails(p.getKey, requiredProperties.contains(p.getKey))
        })
        .toSet
    }
  }
}
