package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import graphql.codegen.AddConsignment
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.types.AddConsignmentInput
import sttp.client3.{Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.Token

import scala.concurrent.Future

class GraphQlApiService(addConsignmentClient: GraphQLClient[ac.Data, ac.Variables])(implicit backend: SttpBackend[Identity, Any]) {

  implicit class FutureUtils[T](f: Future[T]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }

  def addConsignment(token: Token): IO[AddConsignment.addConsignment.AddConsignment] = {
    for {
      result <- addConsignmentClient.getResult(token.bearerAccessToken, ac.document, ac.Variables(AddConsignmentInput(None, "standard")).some).toIO
      data <- IO.fromOption(result.data)(new RuntimeException("Consignment not added"))
    } yield data.addConsignment
  }
}

object GraphQlApiService {
  def apply(addConsignmentClient: GraphQLClient[ac.Data, ac.Variables])(implicit backend: SttpBackend[Identity, Any]) = new GraphQlApiService(addConsignmentClient)
}
