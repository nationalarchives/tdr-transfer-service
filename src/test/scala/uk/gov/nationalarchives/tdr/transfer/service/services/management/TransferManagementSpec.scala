package uk.gov.nationalarchives.tdr.transfer.service.services.management

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => query}
import org.mockito.ArgumentMatchers.any
import sangria.ast.Document
import sttp.client3.{Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.AuthorisationError
import uk.gov.nationalarchives.tdr.transfer.service.services.S3Service
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages

import java.util.UUID
import scala.concurrent.Future
import scala.reflect.ClassTag

class TransferManagementSpec extends BaseSpec {
  private val client = mock[GraphQLClient[query.Data, query.Variables]]
  private val messages = mock[Messages]
  private val s3 = mock[S3Service]
  private val bucket = "export-bucket"
  private val token = mock[Token]
  private val owner = UUID.randomUUID()
  private val first = UUID.randomUUID()
  private val second = UUID.randomUUID()
  private val asset = UUID.randomUUID()
  private val legacy = UUID.randomUUID()

  private def consignment(userId: UUID): query.Data = {
    val data = mock[query.Data]
    val consignment = mock[query.GetConsignment]
    val file = mock[query.GetConsignment.Files]
    val legacyFile = mock[query.GetConsignment.Files]
    val folder = mock[query.GetConsignment.Files]
    val metadata = mock[query.GetConsignment.Files.FileMetadata]
    when(metadata.name).thenReturn("AssetId")
    when(metadata.value).thenReturn(asset.toString)
    when(file.fileType).thenReturn(Some("File"))
    when(file.fileMetadata).thenReturn(List(metadata))
    when(legacyFile.fileType).thenReturn(Some("File"))
    when(legacyFile.fileId).thenReturn(legacy)
    when(legacyFile.fileMetadata).thenReturn(Nil)
    when(folder.fileType).thenReturn(Some("Folder"))
    when(consignment.userid).thenReturn(userId)
    when(consignment.consignmentReference).thenReturn("TDR-2025-TEST")
    when(consignment.files).thenReturn(List(file, legacyFile, folder))
    when(data.getConsignment).thenReturn(Some(consignment))
    data
  }

  private def stubResponses(responses: query.Data*): Unit =
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[query.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(responses.head), Nil)), responses.tail.map(data => Future.successful(GraphQlResponse(Some(data), Nil))): _*)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(client, messages, token, s3)
    when(token.userId).thenReturn(owner)
    when(token.transferServiceRoles).thenReturn(Set.empty)
    when(messages.sendIngestCompleteMessage(any[UUID])).thenReturn(IO.pure(null))
    when(s3.tagBagitPackage(any[String], any[String])).thenReturn(IO.unit)
  }

  "ingestComplete" should "send one event per file using persisted and legacy asset IDs, excluding folders" in {
    stubResponses(consignment(owner))
    new TransferManagement(client, messages, s3, bucket).ingestComplete(token, List(first, first)).unsafeRunSync()
    verify(messages).sendIngestCompleteMessage(asset)
    verify(messages).sendIngestCompleteMessage(legacy)
    verify(messages, times(2)).sendIngestCompleteMessage(any[UUID])
    verify(s3).tagBagitPackage(bucket, "TDR-2025-TEST")
  }

  it should "allow a transfer management administrator to operate on another user's consignment" in {
    when(token.transferServiceRoles).thenReturn(Set("transfer_management"))
    stubResponses(consignment(UUID.randomUUID()))
    new TransferManagement(client, messages, s3, bucket).ingestComplete(token, List(first)).unsafeRunSync()
    verify(messages).sendIngestCompleteMessage(asset)
  }

  it should "authorise every consignment before sending any messages" in {
    stubResponses(consignment(owner), consignment(UUID.randomUUID()))
    intercept[AuthorisationError] {
      new TransferManagement(client, messages, s3, bucket).ingestComplete(token, List(first, second)).unsafeRunSync()
    }
    verify(messages, never).sendIngestCompleteMessage(any[UUID])
    verify(s3, never).tagBagitPackage(any[String], any[String])
  }

  it should "propagate a queue failure without reporting success" in {
    stubResponses(consignment(owner))
    when(messages.sendIngestCompleteMessage(any[UUID])).thenReturn(IO.raiseError(new RuntimeException("queue unavailable")))
    val error = intercept[RuntimeException] {
      new TransferManagement(client, messages, s3, bucket).ingestComplete(token, List(first)).unsafeRunSync()
    }
    error.getMessage shouldBe "queue unavailable"
    verify(s3, never).tagBagitPackage(any[String], any[String])
  }

  it should "propagate failures tagging the BagIt package" in {
    stubResponses(consignment(owner))
    when(s3.tagBagitPackage(bucket, "TDR-2025-TEST")).thenReturn(IO.raiseError(new RuntimeException("tagging failed")))
    val error = intercept[RuntimeException] {
      new TransferManagement(client, messages, s3, bucket).ingestComplete(token, List(first)).unsafeRunSync()
    }
    error.getMessage shouldBe "tagging failed"
    verify(messages).sendIngestCompleteMessage(asset)
  }

  it should "tag each authorised consignment even when it contains no files" in {
    val data = consignment(owner)
    when(data.getConsignment.get.files).thenReturn(Nil)
    stubResponses(data)
    new TransferManagement(client, messages, s3, bucket).ingestComplete(token, List(first)).unsafeRunSync()
    verify(s3).tagBagitPackage(bucket, "TDR-2025-TEST")
    verify(messages, never).sendIngestCompleteMessage(any[UUID])
  }

  it should "tag each distinct consignment's BagIt package" in {
    val firstData = consignment(owner)
    val secondData = consignment(owner)
    when(secondData.getConsignment.get.consignmentReference).thenReturn("TDR-2025-SECOND")
    stubResponses(firstData, secondData)

    new TransferManagement(client, messages, s3, bucket).ingestComplete(token, List(first, second, first)).unsafeRunSync()

    verify(s3).tagBagitPackage(bucket, "TDR-2025-TEST")
    verify(s3).tagBagitPackage(bucket, "TDR-2025-SECOND")
    verify(s3, times(2)).tagBagitPackage(any[String], any[String])
  }
}
