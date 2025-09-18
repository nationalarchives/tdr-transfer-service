package uk.gov.nationalarchives.tdr.transfer.service.services.schema

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

import scala.jdk.CollectionConverters._
import java.io.InputStream
import scala.collection.mutable

object SchemaHandler {
  private def getJsonNodeFromStreamContent(content: InputStream): JsonNode = {
    val mapper = new ObjectMapper()
    mapper.readTree(content)
  }

  def schema(schemaLocation: String): JsonNode = {
    getJsonNodeFromStreamContent(getClass.getResourceAsStream(schemaLocation))
  }

  def getSchemaProperties(schemaNode: JsonNode) = {
    schemaNode.get("properties").properties().asScala
  }
}
