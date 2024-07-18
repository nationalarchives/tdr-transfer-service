package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.AddConsignment
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.GetConsignment
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.types.{AddConsignmentInput, StartUploadInput}
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.appConfig

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GraphQlApiService(
    addConsignmentClient: GraphQLClient[ac.Data, ac.Variables],
    getConsignmentClient: GraphQLClient[gc.Data, gc.Variables],
    startUploadClient: GraphQLClient[su.Data, su.Variables]
)(implicit
    backend: SttpBackend[Identity, Any]
) {

  implicit class FutureUtils[T](f: Future[T]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }

  def addConsignment(accessToken: BearerAccessToken): IO[AddConsignment.addConsignment.AddConsignment] = {
    for {
      result <- addConsignmentClient.getResult(accessToken, ac.document, ac.Variables(AddConsignmentInput(None, "standard")).some).toIO
      data <- IO.fromOption(result.data)(new RuntimeException("Consignment not added"))
    } yield data.addConsignment
  }

  def consignmentDetails(accessToken: BearerAccessToken, consignmentId: UUID): IO[Option[GetConsignment.getConsignment.GetConsignment]] = {
    for {
      result <- getConsignmentClient.getResult(accessToken, gc.document, gc.Variables(consignmentId).some).toIO
      data <- IO.fromOption(result.data)(new RuntimeException("Consignment not added"))
    } yield data.getConsignment
  }

  def startUpload(accessToken: BearerAccessToken, consignmentId: UUID, parentFolder: Option[String] = None): IO[String] = {
    for {
      result <- startUploadClient.getResult(accessToken, su.document, su.Variables(StartUploadInput(consignmentId, parentFolder.getOrElse(""), None)).some).toIO
      data <- IO.fromOption(result.data)(new RuntimeException(s"Load not started for consignment: $consignmentId"))
    } yield data.startUpload
  }
}

object GraphQlApiService {
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  private val apiUrl = appConfig.consignmentApi.url

  val service = GraphQlApiService.apply(
    new GraphQLClient[ac.Data, ac.Variables](apiUrl),
    new GraphQLClient[gc.Data, gc.Variables](apiUrl),
    new GraphQLClient[su.Data, su.Variables](apiUrl)
  )

  def apply(
      addConsignmentClient: GraphQLClient[ac.Data, ac.Variables],
      getConsignmentClient: GraphQLClient[gc.Data, gc.Variables],
      startUploadClient: GraphQLClient[su.Data, su.Variables]
  )(implicit backend: SttpBackend[Identity, Any]) = new GraphQlApiService(addConsignmentClient, getConsignmentClient, startUploadClient)
}
