package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.unsafe.implicits.global
import io.circe.Json
import software.amazon.awssdk.services.s3.model.S3Object
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class S3ServiceSpec extends BaseSpec {

  "getJsonObjectsWithPrefix" should "return empty list when no objects exist" in {
    val s3Utils = mock[S3Utils]
    when(s3Utils.listAllObjectsWithPrefix("bucket", "prefix")).thenReturn(List.empty)
    val service = new S3Service(s3Utils)

    val result = service.getJsonObjectsWithPrefix("prefix", "bucket").unsafeRunSync()

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

    val result = service.getJsonObjectsWithPrefix("prefix", "bucket").unsafeRunSync()

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

    an[Exception] should be thrownBy service.getJsonObjectsWithPrefix("prefix", "bucket").unsafeRunSync()
  }
}
