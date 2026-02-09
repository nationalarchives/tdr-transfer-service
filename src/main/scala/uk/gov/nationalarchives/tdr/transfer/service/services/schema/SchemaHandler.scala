package uk.gov.nationalarchives.tdr.transfer.service.services.schema

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails

import java.io.InputStream

object SchemaHandler {
  private def getJsonNodeFromStreamContent(content: InputStream): JsonNode = {
    val mapper = new ObjectMapper()
    mapper.readTree(content)
  }

  def schema(schemaLocation: String): JsonNode = {
    getJsonNodeFromStreamContent(getClass.getResourceAsStream(schemaLocation))
  }

  def tdrSharePointCustomTags: Set[MetadataPropertyDetails] = {
    val metadataConfiguration = ConfigUtils.loadConfiguration
    val editablePropertiesMapper = metadataConfiguration.downloadFileDisplayProperties("MetadataDownloadTemplate")
    val sharePointTagMapper = metadataConfiguration.propertyToOutputMapper("sharePointTag")
    val tdrFileHeaderMapper = metadataConfiguration.propertyToOutputMapper("tdrFileHeader")
    editablePropertiesMapper.collect {
      case p if p.editable =>
        val propertyKey = p.key
        val propertyName = sharePointTagMapper(propertyKey)
        val displayName = tdrFileHeaderMapper(propertyKey)
        MetadataPropertyDetails(
          propertyName,
          required = false,
          displayName = displayName
        )
    }.toSet
  }
}
