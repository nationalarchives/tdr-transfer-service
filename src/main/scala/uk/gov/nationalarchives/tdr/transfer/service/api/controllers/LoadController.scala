package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import graphql.codegen.AddConsignment.{addConsignment => ac}
import org.http4s.HttpRoutes
import sttp.client3.{Identity, SttpBackend}
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.Configuration
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.AuthenticatedContext
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Serializers._
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class LoadController(graphqlApiService: GraphQlApiService) extends BaseController {
  def endpoints: List[Endpoint[String, Unit, BackendException.AuthenticationError, LoadDetails, Any]] = List(initiateLoadEndpoint.endpoint)

  def routes: HttpRoutes[IO] = initiateLoadRoute

  private val initiateLoadEndpoint: PartialServerEndpoint[String, AuthenticatedContext, Unit, BackendException.AuthenticationError, LoadDetails, Any, IO] = securedWithBearer
    .summary("Initiate the load of records and metadata")
    .post
    .in("load" / "sharepoint" / "initiate")
    .out(jsonBody[LoadDetails])

  private def loadDetails(consignmentId: UUID, userId: UUID): IO[LoadDetails] = {
    val recordsS3Bucket = AWSS3LoadDestination("s3BucketNameRecords", s"$userId/$consignmentId")
    val metadataS3Bucket = AWSS3LoadDestination("s3BucketNameMetadata", s"$consignmentId/dataload/data-load-metadata.csv")
    IO(LoadDetails(consignmentId, recordsLoadDestination = recordsS3Bucket, metadataLoadDestination = metadataS3Bucket))
  }

  val initiateLoadRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      initiateLoadEndpoint.serverLogicSuccess(ac => _ => graphqlApiService.addConsignment(ac.token).flatMap(c => loadDetails(c.consignmentid.get, ac.token.userId)))
    )
}

object LoadController {

  def apply()(implicit backend: SttpBackend[Identity, Any], appConfig: Configuration) = new LoadController(
    GraphQlApiService.apply(
      new GraphQLClient[ac.Data, ac.Variables](appConfig.api.url)
    )
  )
}
