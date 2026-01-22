package uk.gov.nationalarchives.tdr.transfer.service.services.errors

import cats.effect.{IO, Resource}
import cats.implicits._
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import io.circe.Json
import io.circe.parser.parse
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.aws.utils.s3.{S3Clients, S3Utils}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.ConsignmentStatusType.Upload
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.StatusValue.Completed
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.nio.charset.StandardCharsets
import java.util.UUID

class RetrieveErrors(graphQlApiService: GraphQlApiService)(implicit logger: SelfAwareStructuredLogger[IO]) {

  def getErrorsFromS3(token: Token, existingTransferId: Option[UUID] = None): IO[List[Json]] = {
    for {
      consignmentState <- graphQlApiService.consignmentState(token, existingTransferId.get)
      loadState = isUploadFinished(consignmentState.consignmentStatuses)
      _ <- if (loadState) IO.unit else IO.raiseError(new Exception("Upload has not finished"))
      s3Objects <- IO.blocking(RetrieveErrors.s3Utils.listAllObjectsWithPrefix(appConfig.s3.transferErrorsBucketName, s"${existingTransferId.get}"))
      _ <- IO.whenA(s3Objects.isEmpty)(IO.raiseError(new Exception("No error objects found")))
      jsons <- fetchErrorsFromS3(existingTransferId.get)
    } yield jsons
  }

  private def isUploadFinished(state: List[ConsignmentStatuses]): Boolean = {
    val uploadState: Option[ConsignmentStatuses] = state.find(_.statusType == Upload.toString)
    uploadState.nonEmpty && uploadState.get.value == Completed.toString
  }

  private def fetchErrorsFromS3(transferId: UUID): IO[List[Json]] = {
    for {
      s3Objects <- IO.blocking(RetrieveErrors.s3Utils.listAllObjectsWithPrefix(appConfig.s3.transferErrorsBucketName, s"$transferId"))
      _ <- IO.whenA(s3Objects.isEmpty)(IO.raiseError(new Exception("No error objects found")))
      jsons <- s3Objects.traverse { s3Object =>
        val streamRes: Resource[IO, java.io.InputStream] =
          Resource.fromAutoCloseable(IO.blocking(RetrieveErrors.s3Utils.getObjectAsStream(appConfig.s3.transferErrorsBucketName, s3Object.key())))
        streamRes.use { stream =>
          for {
            bytes <- IO.blocking(stream.readAllBytes())
            str = new String(bytes, StandardCharsets.UTF_8)
            json <- IO.fromEither(parse(str).leftMap(err => new Exception(s"Failed to parse JSON from ${s3Object.key()}: ${err.message}")))
          } yield json
        }
      }
    } yield jsons
  }
}

object RetrieveErrors {
  val s3Config: ApplicationConfig.S3 = ApplicationConfig.appConfig.s3
  val s3Utils: S3Utils = S3Utils(S3Clients.s3Async(appConfig.s3.endpoint))
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]) = new RetrieveErrors(GraphQlApiService.service)(logger)
}
