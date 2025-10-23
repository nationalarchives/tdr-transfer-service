package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import graphql.codegen.AddConsignment
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.AddOrUpdateConsignmenetMetadata.{addOrUpdateConsignmentMetadata => acm}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.types.{AddConsignmentInput, AddOrUpdateConsignmentMetadata, AddOrUpdateConsignmentMetadataInput, StartUploadInput}
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GraphQlApiService(
    addConsignmentClient: GraphQLClient[ac.Data, ac.Variables],
    consignmentMetadataClient: GraphQLClient[acm.Data, acm.Variables],
    startUploadClient: GraphQLClient[su.Data, su.Variables]
)(implicit
    backend: SttpBackend[Identity, Any]
) {

  implicit class FutureUtils[T](f: Future[T]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }

  def addConsignment(token: Token, sourceSystem: SourceSystem): IO[AddConsignment.addConsignment.AddConsignment] = {
    for {
      addConsignmentResult <- addConsignmentClient.getResult(token.bearerAccessToken, ac.document, ac.Variables(AddConsignmentInput(None, "standard")).some).toIO
      addConsignmentData <- IO.fromOption(addConsignmentResult.data)(new RuntimeException(s"Consignment not added for user ${token.userId}"))
      consignmentId = addConsignmentData.addConsignment.consignmentid.get
      consignmentMetadata = AddOrUpdateConsignmentMetadata("SourceSystem", sourceSystem.toString)
      consignmentMetadataResult <- consignmentMetadataClient
        .getResult(token.bearerAccessToken, acm.document, acm.Variables(AddOrUpdateConsignmentMetadataInput(consignmentId, List(consignmentMetadata))).some)
        .toIO
      _ <- IO.fromOption(consignmentMetadataResult.data)(new RuntimeException(s"Consignment metadata not added for $consignmentId"))
    } yield addConsignmentData.addConsignment
  }

  def startUpload(token: Token, consignmentId: UUID, parentFolder: Option[String] = None): IO[String] = {
    for {
      result <- startUploadClient.getResult(token.bearerAccessToken, su.document, su.Variables(StartUploadInput(consignmentId, parentFolder.getOrElse(""), None)).some).toIO
      data <- IO.fromOption(result.data)(new RuntimeException(s"Load not started for consignment: $consignmentId"))
    } yield data.startUpload
  }
}

object GraphQlApiService {
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  private val apiUrl = appConfig.consignmentApi.url

  val service: GraphQlApiService = GraphQlApiService.apply(
    new GraphQLClient[ac.Data, ac.Variables](apiUrl),
    new GraphQLClient[acm.Data, acm.Variables](apiUrl),
    new GraphQLClient[su.Data, su.Variables](apiUrl)
  )

  def apply(
      addConsignmentClient: GraphQLClient[ac.Data, ac.Variables],
      consignmentMetadataClient: GraphQLClient[acm.Data, acm.Variables],
      startUploadClient: GraphQLClient[su.Data, su.Variables]
  )(implicit backend: SttpBackend[Identity, Any]) = new GraphQlApiService(addConsignmentClient, consignmentMetadataClient, startUploadClient)
}
