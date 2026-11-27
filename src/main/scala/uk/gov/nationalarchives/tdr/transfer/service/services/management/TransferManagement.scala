package uk.gov.nationalarchives.tdr.transfer.service.services.management

import cats.effect.IO
import cats.implicits._
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => query}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.error.{HttpException, NotAuthorisedError}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.AuthorisationError
import uk.gov.nationalarchives.tdr.transfer.service.services.S3Service
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class TransferManagement(client: GraphQLClient[query.Data, query.Variables], messages: Messages, s3Service: S3Service, exportBucket: String)(implicit
    backend: SttpBackend[Identity, Any]
) {
  private case class ConsignmentAssets(reference: String, assetIds: List[UUID])

  def ingestComplete(token: Token, consignmentIds: List[UUID]): IO[Unit] = {
    def assets(consignmentId: UUID): IO[ConsignmentAssets] =
      for {
        response <- IO.fromFuture(IO.blocking(client.getResult(token.bearerAccessToken, query.document, Some(query.Variables(consignmentId))))).adaptError {
          case error: HttpException if error.response.code == StatusCode.Forbidden =>
            AuthorisationError(s"User ${token.userId} does not have access to consignment: $consignmentId")
        }
        _ <-
          if (response.errors.exists(_.isInstanceOf[NotAuthorisedError]))
            IO.raiseError(AuthorisationError(s"User ${token.userId} does not have access to consignment: $consignmentId"))
          else IO.raiseWhen(response.errors.nonEmpty)(new RuntimeException(s"Failed to retrieve consignment $consignmentId: ${response.errors.map(_.message).mkString(", ")}"))
        data <- IO.fromOption(response.data)(new RuntimeException(s"Failed to retrieve consignment: $consignmentId"))
        consignment <- IO.fromOption(data.getConsignment)(new RuntimeException(s"Consignment not found: $consignmentId"))
        _ <- IO.raiseWhen(!token.transferServiceRoles.contains("transfer_management") && consignment.userid != token.userId)(
          AuthorisationError(s"User ${token.userId} does not own consignment: $consignmentId")
        )
        assetIds <- consignment.files.filter(_.fileType.contains("File")).traverse { file =>
          file.fileMetadata.find(_.name == "AssetId") match {
            case Some(metadata) => IO(UUID.fromString(metadata.value))
            case None           => IO.pure(file.fileId)
          }
        }
      } yield ConsignmentAssets(consignment.consignmentReference, assetIds)

    for {
      consignments <- consignmentIds.distinct.traverse(assets)
      _ <- consignments.flatMap(_.assetIds).traverse_(messages.sendIngestCompleteMessage)
      _ <- consignments.traverse_(consignment => s3Service.tagBagitPackage(exportBucket, consignment.reference))
    } yield ()
  }
}

object TransferManagement {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]): TransferManagement = {
    implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    new TransferManagement(new GraphQLClient[query.Data, query.Variables](appConfig.consignmentApi.url), Messages(), S3Service(), appConfig.s3.consignmentExportBucketName)
  }
}
