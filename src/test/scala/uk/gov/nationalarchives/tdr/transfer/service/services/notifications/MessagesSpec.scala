package uk.gov.nationalarchives.tdr.transfer.service.services.notifications

import uk.gov.nationalarchives.aws.utils.sqs.SQSUtils
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages.AggregateProcessingEvent
import uk.gov.nationalarchives.tdr.transfer.service.{ApplicationConfig, BaseSpec}
import cats.effect.unsafe.implicits.global

import java.util.UUID

class MessagesSpec extends BaseSpec {
  "'sendIngestCompleteMessage'" should "send a DR2-compatible event to the external events queue" in {
    val assetId = UUID.randomUUID()
    val config = mock[ApplicationConfig.Sqs]
    val sqs = mock[SQSUtils]
    when(config.externalEventHandlingQueueUrl).thenReturn("external/queue")

    new Messages(sqs, config).sendIngestCompleteMessage(assetId).unsafeRunSync()

    verify(sqs).send("external/queue", s"""{"properties":{"messageType":"preserve.digital.asset.ingest.complete"},"parameters":{"assetId":"$assetId"}}""")
  }

  "'sendAggregateProcessingEventMessage'" should "send a aggregate processing event message to the correct sqs queue" in {
    val transferId = UUID.randomUUID()
    val mockSqsConfig = mock[ApplicationConfig.Sqs]
    val mockSqsUtils = mock[SQSUtils]
    when(mockSqsConfig.aggregateProcessingQueueUrl).thenReturn("sqs/url")
    val service = new Messages(mockSqsUtils, mockSqsConfig)
    val event = AggregateProcessingEvent(
      "source-bucket",
      "metadata/source/prefix",
      dataLoadErrors = true,
      ignoreSiteName = true,
      loadedNumberOfFiles = 2,
      "records-source-bucket",
      "records/source/prefix"
    )
    val expectedMessageString = """{
                                  |  "metadataSourceBucket" : "source-bucket",
                                  |  "metadataSourceObjectPrefix" : "metadata/source/prefix",
                                  |  "dataLoadErrors" : true,
                                  |  "ignoreSiteName" : true,
                                  |  "loadedNumberOfFiles" : 2,
                                  |  "recordsSourceBucket" : "records-source-bucket",
                                  |  "recordsSourceObjectPrefix" : "records/source/prefix"
                                  |}""".stripMargin

    service.sendAggregateProcessingEventMessage(transferId, event).unsafeRunSync()
    verify(mockSqsUtils).send("sqs/url", expectedMessageString)
  }

  "'sendAggregateProcessingEventMessage'" should "send a aggregate processing event message with default values" in {
    val transferId = UUID.randomUUID()
    val mockSqsConfig = mock[ApplicationConfig.Sqs]
    val mockSqsUtils = mock[SQSUtils]
    when(mockSqsConfig.aggregateProcessingQueueUrl).thenReturn("sqs/url")
    val service = new Messages(mockSqsUtils, mockSqsConfig)
    val event = AggregateProcessingEvent(
      "source-bucket",
      "metadata/source/prefix",
      loadedNumberOfFiles = 2,
      recordsSourceBucket = "records-source-bucket",
      recordsSourceObjectPrefix = "records/source/prefix"
    )
    val expectedMessageString = """{
                                  |  "metadataSourceBucket" : "source-bucket",
                                  |  "metadataSourceObjectPrefix" : "metadata/source/prefix",
                                  |  "dataLoadErrors" : false,
                                  |  "ignoreSiteName" : false,
                                  |  "loadedNumberOfFiles" : 2,
                                  |  "recordsSourceBucket" : "records-source-bucket",
                                  |  "recordsSourceObjectPrefix" : "records/source/prefix"
                                  |}""".stripMargin

    service.sendAggregateProcessingEventMessage(transferId, event).unsafeRunSync()
    verify(mockSqsUtils).send("sqs/url", expectedMessageString)
  }
}
