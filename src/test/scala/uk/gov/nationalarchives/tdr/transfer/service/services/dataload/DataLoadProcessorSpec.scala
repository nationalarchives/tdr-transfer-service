package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.unsafe.implicits.global
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.CustomMetadataModel.CustomPropertyDetails
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum

import java.util.UUID

class DataLoadProcessorSpec extends BaseSpec {

  val transferId = "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"
  val mockKeycloakToken: Token = mock[Token]

  "'trigger' function" should "return the expected result" in {
    val processor = DataLoadProcessor()

    val result = processor.trigger(UUID.fromString(transferId), mockKeycloakToken).unsafeRunSync()
    result shouldBe "Data Load Processor: Stubbed Response"
  }

  "'customMetadataDetails' function" should "return the expect result" in {
    val processor = DataLoadProcessor()
    val mockCustomMetadataDetails = Set(
      CustomPropertyDetails("name_of_property")
    )

    val result =
      processor
        .customMetadataDetails(SourceSystemEnum.SharePoint, UUID.fromString(transferId), mockKeycloakToken, mockCustomMetadataDetails)
        .unsafeRunSync()
    result shouldBe "Custom Metadata Details: Stubbed Response"
  }

}
