package uk.gov.nationalarchives.tdr.transfer.service.api.model

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common._

object CommonSpec extends BaseSpec {
  "ConsignmentStatusType" should "contain the correct enums" in {
    val consignmentStatusType = ConsignmentStatusType
    val expectedValues = List("Upload")

    consignmentStatusType.values.size shouldBe 1
    consignmentStatusType.values.map(_.toString).toList shouldEqual expectedValues
  }

  "StatusValue" should "contain the correct enums" in {
    val statusValue = StatusValue
    val expectedValues = List("Completed", "InProgress")

    statusValue.values.size shouldBe 2
    statusValue.values.map(_.toString).toList shouldEqual expectedValues
  }

  "ObjectCategory" should "contain the correct enums" in {
    val objectCategory = ObjectCategory
    val expectedValues = List("metadata", "records")

    objectCategory.values.size shouldBe 2
    objectCategory.values.map(_.toString).toList shouldEqual expectedValues
  }
}
