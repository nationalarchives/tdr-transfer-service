package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.MetadataPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum

class MetadataLoadConfigurationSpec extends BaseSpec {
  "'metadataLoadConfiguration'" should "return the correct metadata configuration for the given source system" in {
    val sharePointResult = MetadataLoadConfiguration.metadataLoadConfiguration(SourceSystemEnum.SharePoint)
    sharePointResult.size shouldBe 10
    sharePointResult.contains(MetadataPropertyDetails("transferId", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("matchId", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("userId", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("source", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("FileRef", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("FileLeafRef", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("Length", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("SHA256ClientSideChecksum", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("Modified", required = true)) shouldBe true
    sharePointResult.contains(MetadataPropertyDetails("File_x0020_Type", required = true)) shouldBe true

    val hardDriveResult = MetadataLoadConfiguration.metadataLoadConfiguration(SourceSystemEnum.HardDrive)
    hardDriveResult.size shouldBe 0

    val networkDriveResult = MetadataLoadConfiguration.metadataLoadConfiguration(SourceSystemEnum.NetworkDrive)
    networkDriveResult.size shouldBe 0
  }

  "'metadataLoadConfiguration'" should "return an error if source system not mapped to a schema" in {
    val mockSourceSystem = mock[SourceSystemEnum.SourceSystem]

    val exception = intercept[RuntimeException] {
      MetadataLoadConfiguration.metadataLoadConfiguration(mockSourceSystem)
    }

    exception.getMessage shouldBe s"Source System '$mockSourceSystem' not mapped to schema"
  }
}
