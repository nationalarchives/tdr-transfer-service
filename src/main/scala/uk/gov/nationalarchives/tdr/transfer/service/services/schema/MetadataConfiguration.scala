package uk.gov.nationalarchives.tdr.transfer.service.services.schema

import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails

import scala.jdk.CollectionConverters._

object MetadataConfiguration {
  def metadataConfiguration(): Set[MetadataPropertyDetails] = {
    val schema = SchemaHandler.schema().get("allOf")
    val properties = schema.get(0).get("properties").properties().asScala
    val requiredProperties = schema.get(0).get("required").asScala.map(_.asText()).toSet
    properties
      .map(p => {
        MetadataPropertyDetails(p.getKey, requiredProperties.contains(p.getKey))
      })
      .toSet
  }
}
