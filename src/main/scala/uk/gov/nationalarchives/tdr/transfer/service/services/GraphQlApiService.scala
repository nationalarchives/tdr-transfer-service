package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.IO
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId}
import com.typesafe.scalalogging.Logger
import graphql.codegen.AddConsignment
import sttp.client3.{Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.GraphQLClient
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.types.AddConsignmentInput
import uk.gov.nationalarchives.tdr.keycloak.{TdrKeycloakDeployment, Token}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.LoadDetails

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GraphQlApiService()(implicit keycloakDeployment: TdrKeycloakDeployment, backend: SttpBackend[Identity, Any]) {

  implicit class FutureUtils[T](f: Future[T]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }

  private val addConsignmentClient = new GraphQLClient[ac.Data, ac.Variables]("apiUrl")

  def createConsignment(token: Token): IO[AddConsignment.addConsignment.AddConsignment] = {
    for {
      result <- addConsignmentClient.getResult(token.bearerAccessToken, ac.document, ac.Variables(AddConsignmentInput(None, "standard")).some).toIO
      data = result.data.get
    } yield data.addConsignment
  }
}

object GraphQlApiService {
  def apply()(implicit
      backend: SttpBackend[Identity, Any],
      keycloakDeployment: TdrKeycloakDeployment
  ) = new GraphQlApiService
}
