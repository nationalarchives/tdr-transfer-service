package uk.gov.nationalarchives.tdr.transfer.service.services.schema

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails

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

  "'tdrSharePointCustomTags'" should "return the TDR SharePoint custom tag details" in {
    val expectedTdrSharePointCustomTags = Set(
      MetadataPropertyDetails("related_x0020_material", required = false, "related material"),
      MetadataPropertyDetails("is_x0020_description_x0020_closed", required = false, "is description closed"),
      MetadataPropertyDetails("copyright", required = false, "copyright"),
      MetadataPropertyDetails("foi_x0020_schedule_x0020_date", required = false, "foi schedule date"),
      MetadataPropertyDetails("restrictions_x0020_on_x0020_use", required = false, "restrictions on use"),
      MetadataPropertyDetails("evidence_x0020_provided_x0020_by", required = false, "evidence provided by"),
      MetadataPropertyDetails("translated_x0020_filename", required = false, "translated filename"),
      MetadataPropertyDetails("closure_x0020_status", required = false, "closure status"),
      MetadataPropertyDetails("closure_x0020_period", required = false, "closure period"),
      MetadataPropertyDetails("former_x0020_reference", required = false, "former reference"),
      MetadataPropertyDetails("description", required = false, "description"),
      MetadataPropertyDetails("foi_x0020_exemption_x0020_code", required = false, "foi exemption code"),
      MetadataPropertyDetails("alternate_x0020_filename", required = false, "alternate filename"),
      MetadataPropertyDetails("alternate_x0020_description", required = false, "alternate description"),
      MetadataPropertyDetails("is_x0020_filename_x0020_closed", required = false, "is filename closed"),
      MetadataPropertyDetails("closure_x0020_start_x0020_date", required = false, "closure start date"),
      MetadataPropertyDetails("language", required = false, "language"),
      MetadataPropertyDetails("date_x0020_of_x0020_the_x0020_record", required = false, "date of the record")
    )
    val result = SchemaHandler.tdrSharePointCustomTags

    result.size shouldBe 18
    result shouldBe expectedTdrSharePointCustomTags
  }
}
