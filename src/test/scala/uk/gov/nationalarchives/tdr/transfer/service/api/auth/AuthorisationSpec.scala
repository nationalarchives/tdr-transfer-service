package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import graphql.codegen.GetConsignment.{getConsignment => gc}
import org.mockito.ArgumentMatchers.any
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.util.UUID

class AuthorisationSpec extends BaseSpec {
  val graphQlApiService: GraphQlApiService = mock[GraphQlApiService]
  val authorisation = new Authorisation(graphQlApiService)

  val token: Token = mock[Token]
  val consignmentId: UUID = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  val userId: UUID = UUID.fromString("4ab14990-ed63-4615-8336-56fbb9960300")

  "validateUserHasAccessToConsignment" should "succeed when token user id matches consignment userid" in {
    val consignmentObj = mock[gc.GetConsignment]
    when(consignmentObj.userid).thenReturn(userId)
    when(token.userId).thenReturn(userId)
    when(graphQlApiService.getConsignment(any[Token], any[UUID])).thenReturn(IO.pure(consignmentObj))

    noException shouldBe thrownBy {
      authorisation.validateUserHasAccessToConsignment(token, consignmentId).unsafeRunSync()
    }
  }

  it should "raise AuthenticationError when token user id does not match consignment userid" in {
    val consignmentObj = mock[gc.GetConsignment]
    when(consignmentObj.userid).thenReturn(UUID.randomUUID())
    when(token.userId).thenReturn(userId)
    when(graphQlApiService.getConsignment(any[Token], any[UUID])).thenReturn(IO.pure(consignmentObj))

    val ex = intercept[BackendException.AuthenticationError] {
      authorisation.validateUserHasAccessToConsignment(token, consignmentId).unsafeRunSync()
    }

    ex.message should include("User does not have access to this consignment")
    ex.getClass shouldBe classOf[BackendException.AuthenticationError]
  }
}
