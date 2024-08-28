package uk.gov.nationalarchives.tdr.transfer.service.services.schema

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec

class SchemaHandlerSpec extends BaseSpec {
  "'schema'" should "return the specified schema as JsonNode" in {
    val result = SchemaHandler.schema("/metadata-schema/dataLoadSharePointSchema.schema.json")
    result.isEmpty shouldBe false
  }

  "'schema'" should "return an error if schema does not exist" in {
    val exception = intercept[IllegalArgumentException] {
      SchemaHandler.schema("/non-existent/schema/location")
    }
    exception.getMessage shouldBe "argument \"in\" is null"
  }
}
