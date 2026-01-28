package uk.gov.nationalarchives.tdr.transfer.service.services.errors

import cats.effect.IO
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import io.circe.Json
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.ConsignmentStatusType.Upload
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.StatusValue.Completed
import uk.gov.nationalarchives.tdr.transfer.service.services.{GraphQlApiService, S3Service}

import java.util.UUID

class TransferErrors(graphQlApiService: GraphQlApiService, s3Service: S3Service)(implicit logger: SelfAwareStructuredLogger[IO]) {

  def getTransferErrors(token: Token, existingTransferId: Option[UUID] = None): IO[List[Json]] = {
    for {
      consignmentState <- graphQlApiService.consignmentState(token, existingTransferId.get)
      loadState = isUploadFinished(consignmentState.consignmentStatuses)
      _ <- if (loadState) IO.unit else IO.raiseError(new Exception("Upload has not finished"))
      jsons <- s3Service.getJsonObjectsWithPrefix(s"${existingTransferId.get}", appConfig.s3.transferErrorsBucketName)
    } yield jsons
  }

  private def isUploadFinished(state: List[ConsignmentStatuses]): Boolean = {
    val uploadState: Option[ConsignmentStatuses] = state.find(_.statusType == Upload.toString)
    uploadState.nonEmpty && uploadState.get.value == Completed.toString
  }
}

object TransferErrors {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new TransferErrors(GraphQlApiService.service, S3Service())(logger)
}
