package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.services.schema.SchemaHandler

import scala.jdk.CollectionConverters._

object MetadataLoadConfiguration {
  private val schemaConfig: ApplicationConfig.Schema = ApplicationConfig.appConfig.schema

  def metadataLoadConfiguration(): Set[MetadataPropertyDetails] = {
    val schema = SchemaHandler.schema(schemaConfig.dataLoadSharePointLocation)
    val properties = schema.get("properties").properties().asScala
    val requiredProperties = schema.get("required").asScala.map(_.asText()).toSet
    properties
      .map(p => {
        MetadataPropertyDetails(p.getKey, requiredProperties.contains(p.getKey))
      })
      .toSet
  }
}
