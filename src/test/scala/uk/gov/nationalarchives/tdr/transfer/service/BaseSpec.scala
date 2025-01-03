package uk.gov.nationalarchives.tdr.transfer.service

import cats.effect.IO
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.TdrKeycloakDeployment
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{MetadataPropertyDetails, TransferConfiguration}

import scala.concurrent.ExecutionContextExecutor

trait BaseSpec extends AnyFlatSpec with MockitoSugar with Matchers with EitherValues {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment("authUrl", "realm", 60)

  val expectedMetadataPropertyDetails: Set[MetadataPropertyDetails] = Set(
    MetadataPropertyDetails("SHA256ClientSideChecksum", required = true),
    MetadataPropertyDetails("transferId", required = true),
    MetadataPropertyDetails("matchId", required = true),
    MetadataPropertyDetails("Modified", required = true),
    MetadataPropertyDetails("Length", required = true),
    MetadataPropertyDetails("FileRef", required = true),
    MetadataPropertyDetails("FileLeafRef", required = true)
  )

  val expectedTransferConfiguration: TransferConfiguration = TransferConfiguration(3000, 2000, 5000, Set(), expectedMetadataPropertyDetails, display = Set())
}
