package uk.gov.nationalarchives.tdr.transfer.service.api.routes
import cats.effect.unsafe.implicits.global
import org.http4s.implicits._
import org.http4s.{Header, Headers, Method, Request, Status}
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, validUserToken}
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.LoadController
import uk.gov.nationalarchives.tdr.transfer.service.services.ExternalServicesSpec

class UnknownRoutesSpec extends ExternalServicesSpec with Matchers {
  "unknown source system in endpoint" should "return 400 response with correct authorisation header" in {
    graphqlOkJson()
    val validToken = validUserToken()
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$validToken")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .initiateLoadRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/load/unknown/initiate", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.BadRequest
  }

  "unknown source system in endpoint" should "return 400 response with incorrect authorisation header" in {
    val token = invalidToken
    val bearer = CIString("Authorization")
    val authHeader = Header.Raw.apply(bearer, s"$token")
    val fakeHeaders = Headers.apply(authHeader)
    val response = LoadController
      .apply()
      .initiateLoadRoute
      .orNotFound
      .run(
        Request(method = Method.POST, uri = uri"/load/unknown/initiate", headers = fakeHeaders)
      )
      .unsafeRunSync()

    response.status shouldBe Status.BadRequest
  }

}
