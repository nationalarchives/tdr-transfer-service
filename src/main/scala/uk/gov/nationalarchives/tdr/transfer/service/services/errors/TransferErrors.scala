package uk.gov.nationalarchives.tdr.transfer.service.services.errors

import cats.effect.IO
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import io.circe._
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.UploadType
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.InProgressValue
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferErrorResultsModel.TransferErrorsResults
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.TransferErrors.TransferErrorsInput
import uk.gov.nationalarchives.tdr.transfer.service.services.{BaseService, GraphQlApiService, S3Service}

import java.util.UUID

class TransferErrors(graphQlApiService: GraphQlApiService, s3Service: S3Service) extends BaseService[TransferErrorsInput, TransferErrorsResults] {

  def result(input: TransferErrorsInput): IO[Either[BackendError, TransferErrorsResults]] = {
    for {
      statuses <- graphQlApiService.consignmentState(input.token, input.existingTransferId)
      uploadCompleted = isUploadFinished(statuses)
      errorJsons <- if (uploadCompleted) s3Service.getAllJsonObjectsWithPrefix(input.objectPrefix, appConfig.s3.transferErrorsBucketName) else IO.pure(List.empty[Json])
    } yield Right(TransferErrorsResults(uploadCompleted, errorJsons, input.existingTransferId))
  }

  private def isUploadFinished(state: List[ConsignmentStatuses]): Boolean =
    state.find(_.statusType == UploadType.id).exists(s => s.value != InProgressValue.value)
}

object TransferErrors {
  case class TransferErrorsInput(token: Token, existingTransferId: UUID, objectPrefix: String)
  def apply() = new TransferErrors(GraphQlApiService.service, S3Service())
}
