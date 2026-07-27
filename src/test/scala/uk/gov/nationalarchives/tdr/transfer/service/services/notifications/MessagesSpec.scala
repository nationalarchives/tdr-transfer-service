package uk.gov.nationalarchives.tdr.transfer.service.services.notifications

import uk.gov.nationalarchives.aws.utils.sqs.SQSUtils
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages.AggregateProcessingEvent
import uk.gov.nationalarchives.tdr.transfer.service.{ApplicationConfig, BaseSpec}

import java.util.UUID

class MessagesSpec extends BaseSpec {
  "'sendAggregateProcessingEventMessage'" should "send a aggregate processing event message to the correct sqs queue" in {
    val transferId = UUID.randomUUID()
    val mockSqsConfig = mock[ApplicationConfig.Sqs]
    val mockSqsUtils = mock[SQSUtils]
    when(mockSqsConfig.aggregateProcessingQueueUrl).thenReturn("sqs/url")
    val service = new Messages(mockSqsUtils, mockSqsConfig)
    val event = AggregateProcessingEvent("source-bucket", "metadata/source/prefix", dataLoadErrors = true, ignoreSiteName = true, loadedNumberOfFiles = 2)
    val expectedMessageString = """{
                                  |  "metadataSourceBucket" : "source-bucket",
                                  |  "metadataSourceObjectPrefix" : "metadata/source/prefix",
                                  |  "dataLoadErrors" : true,
                                  |  "ignoreSiteName" : true,
                                  |  "loadedNumberOfFiles" : 2
                                  |}""".stripMargin

    service.sendAggregateProcessingEventMessage(transferId, event)
    verify(mockSqsUtils).send("sqs/url", expectedMessageString)
  }

  "'sendAggregateProcessingEventMessage'" should "send a aggregate processing event message with default values" in {
    val transferId = UUID.randomUUID()
    val mockSqsConfig = mock[ApplicationConfig.Sqs]
    val mockSqsUtils = mock[SQSUtils]
    when(mockSqsConfig.aggregateProcessingQueueUrl).thenReturn("sqs/url")
    val service = new Messages(mockSqsUtils, mockSqsConfig)
    val event = AggregateProcessingEvent("source-bucket", "metadata/source/prefix", loadedNumberOfFiles = 2)
    val expectedMessageString = """{
                                  |  "metadataSourceBucket" : "source-bucket",
                                  |  "metadataSourceObjectPrefix" : "metadata/source/prefix",
                                  |  "dataLoadErrors" : false,
                                  |  "ignoreSiteName" : false,
                                  |  "loadedNumberOfFiles" : 2
                                  |}""".stripMargin

    service.sendAggregateProcessingEventMessage(transferId, event)
    verify(mockSqsUtils).send("sqs/url", expectedMessageString)
  }
}
