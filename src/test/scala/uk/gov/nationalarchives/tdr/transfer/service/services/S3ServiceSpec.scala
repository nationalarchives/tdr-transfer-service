package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectTaggingRequest, GetObjectTaggingResponse, PutObjectTaggingRequest, PutObjectTaggingResponse, S3Object, Tag}
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

class S3ServiceSpec extends BaseSpec {

  "tagBagitPackage" should "tag the archive and checksum while preserving existing tags" in {
    val client = mock[S3AsyncClient]
    val existing = Tag.builder().key("existing").value("value").build()
    val oldIngestTag = Tag.builder().key("PreserveDigitalAssetIngest").value("Pending").build()
    when(client.getObjectTagging(any[GetObjectTaggingRequest]))
      .thenReturn(CompletableFuture.completedFuture(GetObjectTaggingResponse.builder().tagSet(existing, oldIngestTag).build()))
    val requests = ArgumentCaptor.forClass(classOf[PutObjectTaggingRequest])
    when(client.putObjectTagging(requests.capture()))
      .thenReturn(CompletableFuture.completedFuture(PutObjectTaggingResponse.builder().build()))

    new S3Service(S3Utils(client)).tagBagitPackage("export-bucket", "TDR-2025-TEST").unsafeRunSync()

    val sent = requests.getAllValues.asScala.toList
    sent.map(_.key()) shouldBe List("TDR-2025-TEST.tar.gz", "TDR-2025-TEST.tar.gz.sha256")
    sent.foreach { request =>
      request.bucket() shouldBe "export-bucket"
      request.tagging().tagSet().asScala.map(tag => tag.key() -> tag.value()).toMap shouldBe
        Map("existing" -> "value", "PreserveDigitalAssetIngest" -> "Complete")
    }
  }

  it should "propagate failures reading existing object tags" in {
    val client = mock[S3AsyncClient]
    when(client.getObjectTagging(any[GetObjectTaggingRequest]))
      .thenReturn(CompletableFuture.failedFuture(new RuntimeException("tag read failed")))
    val error = intercept[Exception] {
      new S3Service(S3Utils(client)).tagBagitPackage("export-bucket", "TDR-2025-TEST").unsafeRunSync()
    }
    error.toString should include("tag read failed")
    verify(client, never).putObjectTagging(any[PutObjectTaggingRequest])
  }

  it should "propagate failures writing object tags" in {
    val client = mock[S3AsyncClient]
    when(client.getObjectTagging(any[GetObjectTaggingRequest]))
      .thenReturn(CompletableFuture.completedFuture(GetObjectTaggingResponse.builder().build()))
    when(client.putObjectTagging(any[PutObjectTaggingRequest]))
      .thenReturn(CompletableFuture.failedFuture(new RuntimeException("tag write failed")))
    val error = intercept[Exception] {
      new S3Service(S3Utils(client)).tagBagitPackage("export-bucket", "TDR-2025-TEST").unsafeRunSync()
    }
    error.toString should include("tag write failed")
  }

  "getJsonObjectsWithPrefix" should "return empty list when no objects exist" in {
    val s3Utils = mock[S3Utils]
    when(s3Utils.listAllObjectsWithPrefix("bucket", "prefix")).thenReturn(List.empty)
    val service = new S3Service(s3Utils)

    val result = service.getAllJsonObjectsWithPrefix("prefix", "bucket").unsafeRunSync()

    result shouldBe empty
    verify(s3Utils, times(1)).listAllObjectsWithPrefix("bucket", "prefix")
  }

  "getJsonObjectsWithPrefix" should "parse JSON for each object" in {
    val s3Utils = mock[S3Utils]
    val obj1 = S3Object.builder().key("k1").build()
    val obj2 = S3Object.builder().key("k2").build()

    when(s3Utils.listAllObjectsWithPrefix("bucket", "prefix")).thenReturn(List(obj1, obj2))
    when(s3Utils.getObjectAsStream("bucket", "k1"))
      .thenReturn(new ByteArrayInputStream("""{"a":1}""".getBytes(StandardCharsets.UTF_8)))
    when(s3Utils.getObjectAsStream("bucket", "k2"))
      .thenReturn(new ByteArrayInputStream("""{"b":2}""".getBytes(StandardCharsets.UTF_8)))

    val service = new S3Service(s3Utils)

    val result = service.getAllJsonObjectsWithPrefix("prefix", "bucket").unsafeRunSync()

    result should contain theSameElementsAs List(Json.obj("a" -> Json.fromInt(1)), Json.obj("b" -> Json.fromInt(2)))
    verify(s3Utils, times(1)).listAllObjectsWithPrefix("bucket", "prefix")
    verify(s3Utils, times(1)).getObjectAsStream("bucket", "k1")
    verify(s3Utils, times(1)).getObjectAsStream("bucket", "k2")
  }

  "getJsonObjectsWithPrefix" should "fail on invalid JSON" in {
    val s3Utils = mock[S3Utils]
    val obj = S3Object.builder().key("bad").build()
    when(s3Utils.listAllObjectsWithPrefix("bucket", "prefix")).thenReturn(List(obj))
    when(s3Utils.getObjectAsStream("bucket", "bad"))
      .thenReturn(new ByteArrayInputStream("""{not-json""".getBytes(StandardCharsets.UTF_8)))

    val service = new S3Service(s3Utils)

    an[Exception] should be thrownBy service.getAllJsonObjectsWithPrefix("prefix", "bucket").unsafeRunSync()
  }

  "getJsonObjectsWithPrefix" should "fail when S3 listAllObjectsWithPrefix throws exception" in {
    val s3Utils = mock[S3Utils]
    when(s3Utils.listAllObjectsWithPrefix("bucket", "prefix"))
      .thenThrow(new RuntimeException("S3 connection failed"))

    val service = new S3Service(s3Utils)

    an[Exception] should be thrownBy service.getAllJsonObjectsWithPrefix("prefix", "bucket").unsafeRunSync()
  }

  "getJsonObjectsWithPrefix" should "fail when S3 getObjectAsStream throws exception" in {
    val s3Utils = mock[S3Utils]
    val obj = S3Object.builder().key("k1").build()
    when(s3Utils.listAllObjectsWithPrefix("bucket", "prefix")).thenReturn(List(obj))
    when(s3Utils.getObjectAsStream("bucket", "k1"))
      .thenThrow(new RuntimeException("Failed to read object"))

    val service = new S3Service(s3Utils)

    an[Exception] should be thrownBy service.getAllJsonObjectsWithPrefix("prefix", "bucket").unsafeRunSync()
  }
}
