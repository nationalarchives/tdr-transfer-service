package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.LoadFailed
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.SourceSystem
import uk.gov.nationalarchives.tdr.transfer.service.services.BaseService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.ClientSideFailure.{ClientSideFailureInput, config}

import java.util.UUID

class ClientSideFailure()(implicit logger: SelfAwareStructuredLogger[IO]) extends BaseService[ClientSideFailureInput, Unit] {
  private val clientSideErrorMessagePattern = config.errorMessagePatterns.clientSideDataLoadError

  def result(input: ClientSideFailureInput): IO[Either[BackendError, Unit]] = {
    IO.pure(Right(logger.error(s"$clientSideErrorMessagePattern ${input.transferId}: ${input.loadFailed.loadError.message}")))
  }
}

object ClientSideFailure {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  val config: ApplicationConfig.Configuration = ApplicationConfig.appConfig
  case class ClientSideFailureInput(token: Token, sourceSystem: SourceSystem, transferId: UUID, loadFailed: LoadFailed)
  def apply() = new ClientSideFailure()
}
