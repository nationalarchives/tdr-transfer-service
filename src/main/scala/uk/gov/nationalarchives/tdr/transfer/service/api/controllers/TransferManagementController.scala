package uk.gov.nationalarchives.tdr.transfer.service.api.controllers

import cats.effect.IO
import org.http4s.HttpRoutes
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.tapir._
import sttp.tapir.json.circe._
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.{AuthenticatedContext, Authorisation}
import uk.gov.nationalarchives.tdr.transfer.service.services.management.TransferManagement

import java.util.UUID

class TransferManagementController(management: TransferManagement) extends BaseController {
  override val authorisation: Authorisation = Authorisation()

  private val ingestCompleteEndpoint = securedWithBearer.post
    .summary("Send ingest-complete events and tag BagIt packages for consignments")
    .in("admin" / "transfer" / "management" / "ingest-complete")
    .in(jsonBody[List[UUID]])

  def endpoints = List(ingestCompleteEndpoint.endpoint)

  override val routes: HttpRoutes[IO] = routesWithAuthorisation(
    ingestCompleteEndpoint,
    (_: AuthenticatedContext, _: List[UUID]) => IO.unit,
    (token, ids: List[UUID]) => management.ingestComplete(token, ids).as(Right(()))
  )
}

object TransferManagementController {
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]): TransferManagementController =
    new TransferManagementController(TransferManagement())
}
