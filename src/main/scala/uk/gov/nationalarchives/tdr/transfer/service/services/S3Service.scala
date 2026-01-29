package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.{IO, Resource}
import cats.implicits._
import io.circe.Json
import io.circe.parser.parse
import uk.gov.nationalarchives.aws.utils.s3.{S3Clients, S3Utils}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig

import java.nio.charset.StandardCharsets

class S3Service(s3Utils: S3Utils) {

  def getAllJsonObjectsWithPrefix(objectPrefix: String, bucketName: String): IO[List[Json]] = {
    for {
      s3Objects <- IO.blocking(s3Utils.listAllObjectsWithPrefix(bucketName, objectPrefix))
      jsons <-
        if (s3Objects.isEmpty) IO.pure(List.empty[Json])
        else
          s3Objects.traverse { s3Object =>
            val streamRes: Resource[IO, java.io.InputStream] =
              Resource.fromAutoCloseable(IO.blocking(s3Utils.getObjectAsStream(bucketName, s3Object.key())))
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

object S3Service {
  private val s3Utils: S3Utils = S3Utils(S3Clients.s3Async(appConfig.s3.endpoint))

  def apply(): S3Service = new S3Service(s3Utils)
}
