package uk.gov.nationalarchives.tdr.transfer.service.services.schema

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

import java.io.InputStream

object SchemaHandler {
  private def getJsonNodeFromStreamContent(content: InputStream): JsonNode = {
    val mapper = new ObjectMapper()
    mapper.readTree(content)
  }

  def schema(schemaLocation: String): JsonNode = {
    getJsonNodeFromStreamContent(getClass.getResourceAsStream(schemaLocation))
  }
}
