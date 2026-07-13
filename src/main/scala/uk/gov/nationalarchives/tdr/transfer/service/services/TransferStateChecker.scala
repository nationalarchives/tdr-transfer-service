package uk.gov.nationalarchives.tdr.transfer.service.services

import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.{ClientChecksType, UploadType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.InProgressValue

object TransferStateChecker {
  case class TransferState(statuses: List[ConsignmentStatuses])

  def canInitiateLoad(state: TransferState): Boolean = {
    val statuses = state.statuses
    val uploadState: Option[ConsignmentStatuses] = statuses.find(_.statusType == UploadType.id)
    uploadState.nonEmpty && uploadState.get.value == InProgressValue.value
  }

  def canProcessLoad(state: TransferState): Boolean = {
    val statuses = state.statuses
    val uploadStatus = statuses.find(_.statusType == UploadType.id)
    val clientChecksStatus = statuses.find(_.statusType == ClientChecksType.id)
    uploadStatus.exists(_.value == InProgressValue.value) && clientChecksStatus.exists(_.value == InProgressValue.value)
  }
}
