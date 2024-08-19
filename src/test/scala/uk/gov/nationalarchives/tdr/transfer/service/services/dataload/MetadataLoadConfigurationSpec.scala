package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum

class MetadataLoadConfigurationSpec extends BaseSpec {
  "'metadataLoadConfiguration'" should "return the correct metadata configuration for the given source system" in {
    val result = MetadataLoadConfiguration.metadataLoadConfiguration(SourceSystemEnum.SharePoint)
    result.size shouldBe 4
    result.contains(MetadataPropertyDetails("FileRef", true)) shouldBe true
    result.contains(MetadataPropertyDetails("File_x0020_Size", true)) shouldBe true
    result.contains(MetadataPropertyDetails("SHA256ClientSideChecksum", true)) shouldBe true
    result.contains(MetadataPropertyDetails("Modified", true)) shouldBe true
  }

  "'metadataLoadConfiguration'" should "return an error if source system not mapped to a schema" in {
    val mockSourceSystem = mock[SourceSystemEnum.SourceSystem]

    val exception = intercept[RuntimeException] {
      MetadataLoadConfiguration.metadataLoadConfiguration(mockSourceSystem)
    }

    exception.getMessage shouldBe s"Source System '$mockSourceSystem' not mapped to schema"
  }
}