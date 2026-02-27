package uk.gov.nationalarchives.tdr.transfer.service.api.middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s.{Header, HttpRoutes, Request, Response, Status}
import org.typelevel.ci.CIStringSyntax

object SecurityHeaders {
  private def addSecurityHeaders(resp: Response[IO]) =
    resp match {
      case Status.Successful(resp) => resp.putHeaders(
        Header.Raw.apply(ci"X-Frame-Options", "Vary"),
        Header.Raw.apply(ci"MyHeader", "Hello"))
      case resp => resp
    }

  def apply(service: HttpRoutes[IO]): Kleisli[({type λ[β$0$] = OptionT[IO, β$0$]})#λ, Request[IO], Response[IO]] =
    service.map(addSecurityHeaders)
}
