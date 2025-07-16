package uk.gov.nationalarchives.tdr.transfer.service.services.transfermanagement

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferState.StateTypeEnum.StateType
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferState.StateValueEnum.StateValue
import uk.gov.nationalarchives.tdr.transfer.service.services.transfermanagement.TransferManagement.TransferState

import java.util.UUID

class TransferManagement()(implicit logger: SelfAwareStructuredLogger[IO]) {

  def uploadProcessing(token: Token, sourceSystem: SourceSystem, consignmentId: UUID, transferState: TransferState): IO[String] = {
    // TODO: update consignment status
    // TODO: trigger backend checks SFN
    // TODO: [Optional] trigger draft metadata validation
    // TODO: send user notification email
    IO("Upload Processing: Stubbed Response")
  }
}

object TransferManagement {
  case class TransferState(stateType: StateType, stateValue: StateValue)
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TransferManagement()
}
