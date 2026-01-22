package uk.gov.nationalarchives.tdr.transfer.service.services.errors

import cats.effect.{IO, Resource}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.aws.utils.s3.{S3Clients, S3Utils}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.ConsignmentStatusType.Upload
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.ObjectCategory.{MetadataCategory, RecordsCategory}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.StatusValue.InProgress
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadInitiation.s3Config
import uk.gov.nationalarchives.tdr.transfer.service.services.errors.RetrieveErrors.s3Utils
import cats.implicits._
import io.circe.Json
import io.circe.parser.parse

import java.nio.charset.StandardCharsets
import java.util.UUID

class RetrieveErrors(graphQlApiService: GraphQlApiService)(implicit logger: SelfAwareStructuredLogger[IO]) {
  def getErrorsFromS3(token: Token, sourceSystem: SourceSystem, existingTransferId: Option[UUID] = None): IO[List[Json]] = {
    for {
      // Check if uploaded has finished before retrieving errors

      // if upload is finished, retrieve errors from S3 else return an error message
      s3Objects <- IO.blocking(s3Utils.listAllObjectsWithPrefix(appConfig.s3.transferErrorsBucketName, s"${existingTransferId.get}"))
      _ <- IO.whenA(s3Objects.isEmpty)(IO.raiseError(new Exception("No error objects found")))
      jsons <- s3Objects.traverse { s3Object =>
        IO.blocking {
          val stream = s3Utils.getObjectAsStream(appConfig.s3.transferErrorsBucketName, s3Object.key())
          try {
            val bytes = stream.readAllBytes()
            new String(bytes, StandardCharsets.UTF_8)
          } finally {
            stream.close()
          }
        }.flatMap { str =>
          IO.fromEither(
            parse(str).leftMap(err => new Exception(s"Failed to parse JSON from ${s3Object.key()}: ${err.message}"))
          )
        }
      }
    } yield jsons
  }

  def getErrorsFromS3v2(token: Token, sourceSystem: SourceSystem, existingTransferId: Option[UUID] = None): IO[List[Json]] = {
    for {
      s3ObjectsJ <- IO.blocking(RetrieveErrors.s3Utils.listAllObjectsWithPrefix(appConfig.s3.transferErrorsBucketName, s"${existingTransferId.get}"))
      s3Objects = s3ObjectsJ
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
