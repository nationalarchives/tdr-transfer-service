package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.unsafe.implicits.global
import org.mockito.MockitoSugar.mock
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID

class ConsignmentServiceSpec extends AnyFlatSpec with Matchers {
  val mockKeycloakToken: Token = mock[Token]

  "'createConsignment'" should "return the consignment id of the created consignment" in {
    val response = ConsignmentService.apply().createConsignment(mockKeycloakToken).unsafeRunSync()

    response.consignmentId shouldBe UUID.fromString("ae4b7cad-ee83-46bd-b952-80bc8263c6c2")
  }
}
