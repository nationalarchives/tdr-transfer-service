package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.AddOrUpdateConsignmenetMetadata.{addOrUpdateConsignmentMetadata => acm}
import graphql.codegen.GetConsignment.getConsignment.GetConsignment
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => getStatus}
import graphql.codegen.GetConsignmentSummary.{getConsignmentSummary => getSummary}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.types.{AddConsignmentInput, AddOrUpdateConsignmentMetadata, AddOrUpdateConsignmentMetadataInput, StartUploadInput}
import graphql.codegen.{AddConsignment, GetConsignmentStatus, GetConsignmentSummary}
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
    startUploadClient: GraphQLClient[su.Data, su.Variables],
    existingConsignmentClient: GraphQLClient[getSummary.Data, getSummary.Variables],
    consignmentStateClient: GraphQLClient[getStatus.Data, getStatus.Variables],
    getConsignmentClient: GraphQLClient[gc.Data, gc.Variables]
)(implicit
    backend: SttpBackend[Identity, Any]
) {

  implicit class FutureUtils[T](f: Future[T]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }

  def getConsignment(token: Token, consignmentId: UUID): IO[GetConsignment] = {
    for {
      consignmentResult <- getConsignmentClient.getResult(token.bearerAccessToken, gc.document, gc.Variables(consignmentId).some).toIO
      consignmentData <- IO.fromOption(consignmentResult.data)(new RuntimeException(s"Failed to retrieve consignment information for consignment: $consignmentId"))
    } yield consignmentData.getConsignment.get
  }

  def existingConsignment(token: Token, consignmentId: UUID): IO[GetConsignmentSummary.getConsignmentSummary.GetConsignment] = {
    for {
      summaryResult <- existingConsignmentClient.getResult(token.bearerAccessToken, getSummary.document, getSummary.Variables(consignmentId).some).toIO
      summaryData <- IO.fromOption(summaryResult.data)(new RuntimeException(s"Failed to retrieve summary information for consignment: $consignmentId"))
    } yield summaryData.getConsignment.get
  }

  def consignmentState(token: Token, consignmentId: UUID): IO[GetConsignmentStatus.getConsignmentStatus.GetConsignment] = {
    for {
      consignmentState <- consignmentStateClient.getResult(token.bearerAccessToken, getStatus.document, getStatus.Variables(consignmentId).some).toIO
      stateData <- IO.fromOption(consignmentState.data)(new RuntimeException(s"Failed to retrieve state for consignment: $consignmentId"))
    } yield stateData.getConsignment.get
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
    new GraphQLClient[su.Data, su.Variables](apiUrl),
    new GraphQLClient[getSummary.Data, getSummary.Variables](apiUrl),
    new GraphQLClient[getStatus.Data, getStatus.Variables](apiUrl),
    new GraphQLClient[gc.Data, gc.Variables](apiUrl)
  )

  def apply(
      addConsignmentClient: GraphQLClient[ac.Data, ac.Variables],
      consignmentMetadataClient: GraphQLClient[acm.Data, acm.Variables],
      startUploadClient: GraphQLClient[su.Data, su.Variables],
      getConsignmentSummaryClient: GraphQLClient[getSummary.Data, getSummary.Variables],
      getConsignmentStatus: GraphQLClient[getStatus.Data, getStatus.Variables],
      getConsignment: GraphQLClient[gc.Data, gc.Variables]
  )(implicit backend: SttpBackend[Identity, Any]) =
    new GraphQlApiService(addConsignmentClient, consignmentMetadataClient, startUploadClient, getConsignmentSummaryClient, getConsignmentStatus, getConsignment)
}
