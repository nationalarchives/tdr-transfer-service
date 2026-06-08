package uk.gov.nationalarchives.tdr.transfer.service.api.model

import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common._

object CommonSpec extends BaseSpec {
  "TransferFunction" should "contain the correct enums" in {
    val transferFunction = TransferFunction
    val expectedValues = List("errors", "load")

    transferFunction.values.size shouldBe 2
    transferFunction.values.map(_.toString).toList shouldEqual expectedValues
  }
}
