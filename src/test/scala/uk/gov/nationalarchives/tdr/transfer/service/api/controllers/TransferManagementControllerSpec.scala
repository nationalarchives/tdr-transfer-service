package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.{Header, Headers, Method, Request, Status}
import org.mockito.ArgumentMatchers.any
import org.typelevel.ci.CIString
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.TestUtils.{invalidToken, validClientToken}
import uk.gov.nationalarchives.tdr.transfer.service.services.management.TransferManagement

import java.util.UUID

class TransferManagementControllerSpec extends BaseSpec {
  private val management = mock[TransferManagement]
  private def request(path: String, bearer: Option[String], body: String): Request[IO] = {
    val headers = Headers(bearer.toList.map(value => Header.Raw(CIString("Authorization"), value)))
    Request[IO](Method.POST, org.http4s.Uri.unsafeFromString(path), headers = headers)
      .withEntity(body)
      .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))
  }

  "transfer management endpoint" should "accept an authenticated request with consignment IDs" in {
    val id = UUID.randomUUID()
    when(management.ingestComplete(any[Token], any[List[UUID]])).thenReturn(IO.unit)
    val response = new TransferManagementController(management).routes
      .orNotFound(
        request("/admin/transfer/management/ingest-complete", Some(validClientToken(role = "transfer_management").toString), s"""["$id"]""")
      )
      .unsafeRunSync()
    response.status shouldBe Status.Ok
    verify(management).ingestComplete(any[Token], org.mockito.ArgumentMatchers.eq(List(id)))
  }

  it should "reject missing and invalid bearer tokens" in {
    val routes = new TransferManagementController(management).routes.orNotFound
    routes(request("/admin/transfer/management/ingest-complete", None, "[]")).unsafeRunSync().status shouldBe Status.Unauthorized
    routes(request("/admin/transfer/management/ingest-complete", Some(invalidToken.toString), "[]")).unsafeRunSync().status shouldBe Status.Unauthorized
  }

  it should "reject malformed IDs and unknown operations" in {
    val routes = new TransferManagementController(management).routes.orNotFound
    val token = Some(validClientToken(role = "transfer_management").toString)
    routes(request("/admin/transfer/management/ingest-complete", token, """["not-a-uuid"]""")).unsafeRunSync().status shouldBe Status.BadRequest
    routes(request("/admin/transfer/management/unknown", token, "[]")).unsafeRunSync().status shouldBe Status.NotFound
  }
}
