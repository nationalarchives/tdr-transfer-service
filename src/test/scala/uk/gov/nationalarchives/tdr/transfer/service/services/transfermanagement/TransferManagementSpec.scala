package uk.gov.nationalarchives.tdr.transfer.service.services.transfermanagement

import cats.effect.unsafe.implicits.global
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferState.{StateTypeEnum, StateValueEnum}
import uk.gov.nationalarchives.tdr.transfer.service.services.transfermanagement.TransferManagement.TransferState

import java.util.UUID

class TransferManagementSpec extends BaseSpec {

  "'uploadProcessing' function" should "return the expected result" in {
    val mockToken: Token = mock[Token]
    val transferId = "6e3b76c4-1745-4467-8ac5-b4dd736e1b3e"

    val transferManagement = new TransferManagement()

    val result = transferManagement
      .uploadProcessing(mockToken, SourceSystemEnum.SharePoint, UUID.fromString(transferId), TransferState(StateTypeEnum.Upload, StateValueEnum.Completed))
      .unsafeRunSync()
    result shouldBe "Upload Processing: Stubbed Response"
  }

}
