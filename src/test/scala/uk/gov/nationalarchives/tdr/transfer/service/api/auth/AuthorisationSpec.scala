package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentMatchers.any
import uk.gov.nationalarchives.tdr.common.utils.authorisation.{Allow, ConsignmentAuthorisation, ConsignmentAuthorisationInput, Deny}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendException

import java.util.UUID

class AuthorisationSpec extends BaseSpec {
  val authModule: ConsignmentAuthorisation = mock[ConsignmentAuthorisation]
  val authorisation: Authorisation = new Authorisation(authModule)

  val token: Token = mock[Token]
  val consignmentId: UUID = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  val userId: UUID = UUID.fromString("4ab14990-ed63-4615-8336-56fbb9960300")

  "validateUserHasAccessToConsignment" should "succeed when user has access to consignment" in {
    when(token.userId).thenReturn(userId)
    when(authModule.hasAccess(any[ConsignmentAuthorisationInput])).thenReturn(IO(Allow))

    noException shouldBe thrownBy {
      authorisation.validateUserHasAccessToConsignment(token, consignmentId).unsafeRunSync()
    }
  }

  it should "raise AuthenticationError when user does not have access to consignment" in {
    when(token.userId).thenReturn(userId)
    when(authModule.hasAccess(any[ConsignmentAuthorisationInput])).thenReturn(IO(Deny))

    val ex = intercept[BackendException.AuthenticationError] {
      authorisation.validateUserHasAccessToConsignment(token, consignmentId).unsafeRunSync()
    }

    ex.message should include(s"User $userId does not have access to consignment: $consignmentId")
    ex.getClass shouldBe classOf[BackendException.AuthenticationError]
  }
}
