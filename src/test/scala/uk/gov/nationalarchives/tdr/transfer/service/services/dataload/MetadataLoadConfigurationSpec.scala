package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails

class MetadataLoadConfigurationSpec extends BaseSpec {
  "'metadataLoadConfiguration'" should "return the correct metadata configuration for the given source system" in {
    val result = MetadataLoadConfiguration.metadataLoadConfiguration()
    result.size shouldBe 4
    result.contains(MetadataPropertyDetails("FileRef", true)) shouldBe true
    result.contains(MetadataPropertyDetails("File_x0020_Size", true)) shouldBe true
    result.contains(MetadataPropertyDetails("SHA256ClientSideChecksum", true)) shouldBe true
    result.contains(MetadataPropertyDetails("Modified", true)) shouldBe true
  }
}
