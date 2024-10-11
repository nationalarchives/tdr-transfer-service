package uk.gov.nationalarchives.tdr.transfer.service.api.interceptors

import cats.effect.IO
import sttp.model.{Method, StatusCode}
import sttp.tapir.server.interceptor.cors.CORSConfig._
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig

object CustomInterceptors {
  private val permittedOrigins = ApplicationConfig.appConfig.cors.permittedOrigins

  private val corsConfig = CORSConfig.apply(
    AllowedOrigin.Matching(checkOrigins),
    AllowedCredentials.Allow,
    AllowedMethods.Some(Set(Method.GET, Method.HEAD, Method.POST, Method.PUT, Method.DELETE)),
    AllowedHeaders.Reflect,
    ExposedHeaders.None,
    maxAge = MaxAge.Default,
    preflightResponseStatusCode = StatusCode.NoContent
  )

  private def checkOrigins(origin: String): Boolean = {
    permittedOrigins.exists(origin.contains)
  }

  val customCorsInterceptor: CORSInterceptor[IO] = CORSInterceptor.customOrThrow[IO](corsConfig)
}
