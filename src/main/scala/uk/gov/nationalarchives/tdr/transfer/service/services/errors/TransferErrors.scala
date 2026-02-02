package uk.gov.nationalarchives.tdr.transfer.service.services.errors

import cats.effect.IO
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import io.circe._
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.ConsignmentStatusType.Upload
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.StatusValue.Completed
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferErrorResultsModel.TransferErrorsResults
import uk.gov.nationalarchives.tdr.transfer.service.services.{GraphQlApiService, S3Service}

import java.util.UUID

class TransferErrors(graphQlApiService: GraphQlApiService, s3Service: S3Service) {

  def getTransferErrors(token: Token, existingTransferId: UUID, objectPrefix: String): IO[TransferErrorsResults] = {
    for {
      consignmentState <- graphQlApiService.consignmentState(token, existingTransferId)
      uploadCompleted = isUploadFinished(consignmentState.consignmentStatuses)
      errorJsons <- if (uploadCompleted) s3Service.getAllJsonObjectsWithPrefix(objectPrefix, appConfig.s3.transferErrorsBucketName) else IO.pure(List.empty[Json])
    } yield TransferErrorsResults(uploadCompleted, errorJsons, existingTransferId)
  }

  private def isUploadFinished(state: List[ConsignmentStatuses]): Boolean =
    state.find(_.statusType == Upload.toString).exists(_.value == Completed.toString)
}

object TransferErrors {
  def apply() = new TransferErrors(GraphQlApiService.service, S3Service())
}
