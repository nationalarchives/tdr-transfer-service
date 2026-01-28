package uk.gov.nationalarchives.tdr.transfer.service.services.errors

import cats.effect.IO
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import io.circe._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.ConsignmentStatusType.Upload
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.StatusValue.Completed
import uk.gov.nationalarchives.tdr.transfer.service.api.model.TransferErrorModel.TransferErrorsResults
import uk.gov.nationalarchives.tdr.transfer.service.services.{GraphQlApiService, S3Service}

import java.util.UUID

class TransferErrors(graphQlApiService: GraphQlApiService, s3Service: S3Service)(implicit logger: SelfAwareStructuredLogger[IO]) {

  def getTransferErrors(token: Token, existingTransferId: Option[UUID] = None): IO[TransferErrorsResults] = {
    existingTransferId match {
      case None             => IO.raiseError(new IllegalArgumentException("existingTransferId is required"))
      case Some(transferId) =>
        for {
          consignmentState <- graphQlApiService.consignmentState(token, transferId)
          loadState = isUploadFinished(consignmentState.consignmentStatuses)
          errorJsons <- if (loadState) s3Service.getJsonObjectsWithPrefix(s"$transferId", appConfig.s3.transferErrorsBucketName) else IO.pure(List.empty[Json])
        } yield TransferErrorsResults(loadState, errorJsons, transferId)
    }
  }

  private def isUploadFinished(state: List[ConsignmentStatuses]): Boolean =
    state.find(_.statusType == Upload.toString).exists(_.value == Completed.toString)
}

object TransferErrors {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TransferErrors(GraphQlApiService.service, S3Service())(logger)
}
