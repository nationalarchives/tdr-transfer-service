package uk.gov.nationalarchives.tdr.transfer.service

import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.TdrKeycloakDeployment
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{MetadataPropertyDetails, TransferConfiguration}

import scala.concurrent.ExecutionContextExecutor

trait BaseSpec extends AnyFlatSpec with MockitoSugar with Matchers with EitherValues {
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment("authUrl", "realm", 60)

  val expectedMetadataPropertyDetails: Set[MetadataPropertyDetails] = Set(
    MetadataPropertyDetails("Modified", true),
    MetadataPropertyDetails("SHA256ClientSideChecksum", true),
    MetadataPropertyDetails("File_x0020_Size", true),
    MetadataPropertyDetails("FileRef", true)
  )

  val expectedTransferConfiguration: TransferConfiguration = TransferConfiguration(expectedMetadataPropertyDetails)
}
