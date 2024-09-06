package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.unsafe.implicits.global
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum

class DataLoadConfigurationSpec extends BaseSpec {
  private val sourceSystem = SourceSystemEnum.SharePoint

  "'configuration'" should "return expected 'TransferConfiguration' object" in {
    val expectedResult = expectedTransferConfiguration

    val service = new DataLoadConfiguration
    val result = service.configuration(sourceSystem).unsafeRunSync()
    result shouldBe expectedResult
  }
}
