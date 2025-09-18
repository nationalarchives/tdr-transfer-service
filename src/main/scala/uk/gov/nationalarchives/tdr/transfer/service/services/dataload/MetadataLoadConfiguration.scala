package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.schema.SchemaHandler

import scala.jdk.CollectionConverters._

object MetadataLoadConfiguration {
  private val schemaConfig: ApplicationConfig.Schema = ApplicationConfig.appConfig.schema
  private val config = ConfigUtils.loadConfiguration
  private val sharePointTagToPropertyMapper = config.inputToPropertyMapper("sharePointTag")
  private val tdrPropertyMapper = config.propertyToOutputMapper("tdrFileHeader")

  private def sourceSystemSchemaMapping(sourceSystem: SourceSystem): String = sourceSystem match {
    case SourceSystemEnum.SharePoint => schemaConfig.dataLoadSharePointLocation
    case _                           => throw new RuntimeException(s"Source System '$sourceSystem' not mapped to schema")
  }

  def metadataLoadConfiguration(sourceSystem: SourceSystem): Set[MetadataPropertyDetails] = {
    val schemaLocation = sourceSystemSchemaMapping(sourceSystem)
    val schema = SchemaHandler.schema(schemaLocation)
    val properties = schema.get("properties").properties().asScala
    val propertyDescriptions = properties.map(n => n.getKey -> n.getValue.get("description")).toMap
    val requiredProperties = schema.get("required").asScala.map(_.asText()).toSet
    properties
      .map(p => {
        val propertyName = sharePointTagToPropertyMapper(p.getKey)
        val displayName = tdrPropertyMapper(propertyName)
        val description: String = propertyDescriptions.get(propertyName) match {
          case Some(value) if value != null => value.asText()
          case _                            => ""
        }
        MetadataPropertyDetails(p.getKey, requiredProperties.contains(p.getKey), displayName, description)
      })
      .toSet
  }
}
