package uk.gov.nationalarchives.tdr.transfer.service.api.model

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec

class ObjectCategorySpec extends BaseSpec {
  "SourceSystem" should "contain the correct enums" in {
    val objectCategoryValue = ObjectCategory.ObjectCategoryEnum
    val expectedValues = List("dryrunmetadata", "dryrunrecords", "metadata", "records")

    objectCategoryValue.values.size shouldBe 4
    objectCategoryValue.values.map(_.toString).toList shouldEqual expectedValues
  }
}
