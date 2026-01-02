package uk.gov.nationalarchives.tdr.transfer.service.api.model

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec

class SourceSystemSpec extends BaseSpec {
  "SourceSystem" should "contain the correct enums" in {
    val sourceSystemValue = SourceSystem.SourceSystemEnum
    val expectedValues = List("harddrive", "networkdrive", "sharepoint")

    sourceSystemValue.values.size shouldBe 3
    sourceSystemValue.values.map(_.toString).toList shouldEqual expectedValues
  }
}
