package uk.gov.nationalarchives.tdr.transfer.service.services

import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3, TableFor4}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.{ClientChecksType, UploadType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.{CompletedValue, CompletedWithIssuesValue, InProgressValue}
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.services.TransferStateChecker.TransferState

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

class TransferStateCheckerSpec extends BaseSpec with TableDrivenPropertyChecks {
  private val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
  private val transferId: UUID = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")

  val canInitiateLoadScenarios: TableFor3[String, TransferState, Boolean] = Table(
    ("Scenario", "Transfer State", "Result"),
    (
      "correct transfer state",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, InProgressValue.value, someDateTime, None)
        )
      ),
      true
    ),
    (
      "upload status not in progress",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, CompletedValue.value, someDateTime, None)
        )
      ),
      false
    ),
    (
      "no upload status",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, InProgressValue.value, someDateTime, None)
        )
      ),
      false
    ),
    ("empty statuses", TransferState(Nil), false)
  )

  forAll(canInitiateLoadScenarios) { (scenario, transferState, result) =>
    {
      s"canInitiateLoad" should s"return the correct result for $scenario" in {
        TransferStateChecker.canInitiateLoad(transferState) shouldBe result
      }
    }
  }

  val canProcessLoadScenarios: TableFor3[String, TransferState, Boolean] = Table(
    ("Scenario", "Transfer State", "Result"),
    (
      "correct transfer state",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, InProgressValue.value, someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, InProgressValue.value, someDateTime, None)
        )
      ),
      true
    ),
    (
      "upload status not in progress",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, CompletedValue.value, someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, InProgressValue.value, someDateTime, None)
        )
      ),
      false
    ),
    (
      "client checks status not in progress",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, InProgressValue.value, someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, CompletedWithIssuesValue.value, someDateTime, None)
        )
      ),
      false
    ),
    (
      "neither upload or client checks statuses in progress",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, CompletedValue.value, someDateTime, None),
          ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, CompletedWithIssuesValue.value, someDateTime, None)
        )
      ),
      false
    ),
    (
      "no upload status",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, InProgressValue.value, someDateTime, None)
        )
      ),
      false
    ),
    (
      "no client checks status",
      TransferState(
        List(
          ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, InProgressValue.value, someDateTime, None)
        )
      ),
      false
    ),
    ("empty statuses", TransferState(Nil), false)
  )

  forAll(canProcessLoadScenarios) { (scenario, transferState, result) =>
    {
      s"canProcessLoad" should s"return the correct result for $scenario" in {
        TransferStateChecker.canProcessLoad(transferState) shouldBe result
      }
    }
  }
}
