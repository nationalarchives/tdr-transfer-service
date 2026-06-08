package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{LoadCompletion, LoadError}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadProcessor.DataLoadProcessorEvent
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages.AggregateProcessingEvent
import uk.gov.nationalarchives.tdr.transfer.service.{ApplicationConfig, BaseSpec}

import java.util.UUID

class DataLoadProcessorSpec extends BaseSpec {
  val userId: UUID = UUID.randomUUID()
  val transferId: UUID = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  val mockKeycloakToken: Token = mock[Token]
  val mockConfig: ApplicationConfig.Configuration = mock[ApplicationConfig.Configuration]
  val mockS3Config: ApplicationConfig.S3 = mock[ApplicationConfig.S3]
  val mockTransferConfig: ApplicationConfig.TransferConfiguration = mock[ApplicationConfig.TransferConfiguration]

  "'trigger' function" should "send aggregate processing SQS event message and return the correct result when no errors" in {
    val mockMessageService = mock[Messages]
    val transferIdArgumentCaptor: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
    val eventArgumentCaptor: ArgumentCaptor[AggregateProcessingEvent] = ArgumentCaptor.forClass(classOf[AggregateProcessingEvent])

    mockResponses()

    when(mockMessageService.sendAggregateProcessingEventMessage(transferIdArgumentCaptor.capture(), eventArgumentCaptor.capture()))
      .thenReturn(SendMessageResponse.builder().build())

    val processor = new DataLoadProcessor(mockMessageService, mockConfig)
    val details = LoadCompletion(2, 2)
    val event = DataLoadProcessorEvent(SourceSystemEnum.SharePoint, transferId, Some(false), details)

    val result = processor.trigger(event, mockKeycloakToken).unsafeRunSync()
    result.transferId shouldBe transferId
    result.success shouldBe true

    transferIdArgumentCaptor.getValue shouldBe transferId
    eventArgumentCaptor.getValue.dataLoadErrors shouldBe false
    eventArgumentCaptor.getValue.metadataSourceObjectPrefix shouldBe s"$userId/sharepoint/$transferId/metadata"
    eventArgumentCaptor.getValue.metadataSourceBucket shouldBe "source-bucket"
    eventArgumentCaptor.getValue.ignoreSiteName shouldBe false
  }

  "'trigger' function" should "send aggregate processing SQS event message and return the correct result when ignore site name" in {
    val mockMessageService = mock[Messages]
    val transferIdArgumentCaptor: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
    val eventArgumentCaptor: ArgumentCaptor[AggregateProcessingEvent] = ArgumentCaptor.forClass(classOf[AggregateProcessingEvent])

    mockResponses(ignoreSiteNameBodies = "TDR-BODY1;TDR-BODY2")

    when(mockMessageService.sendAggregateProcessingEventMessage(transferIdArgumentCaptor.capture(), eventArgumentCaptor.capture()))
      .thenReturn(SendMessageResponse.builder().build())

    val processor = new DataLoadProcessor(mockMessageService, mockConfig)
    val details = LoadCompletion(2, 2)
    val event = DataLoadProcessorEvent(SourceSystemEnum.SharePoint, transferId, Some(false), details)

    val result = processor.trigger(event, mockKeycloakToken).unsafeRunSync()
    result.transferId shouldBe transferId
    result.success shouldBe true

    transferIdArgumentCaptor.getValue shouldBe transferId
    eventArgumentCaptor.getValue.dataLoadErrors shouldBe false
    eventArgumentCaptor.getValue.metadataSourceObjectPrefix shouldBe s"$userId/sharepoint/$transferId/metadata"
    eventArgumentCaptor.getValue.metadataSourceBucket shouldBe "source-bucket"
    eventArgumentCaptor.getValue.ignoreSiteName shouldBe true
  }

  "'trigger' function" should "send aggregate processing SQS event message and return the correct result when data load errors present" in {
    val mockMessageService = mock[Messages]
    val transferIdArgumentCaptor: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
    val eventArgumentCaptor: ArgumentCaptor[AggregateProcessingEvent] = ArgumentCaptor.forClass(classOf[AggregateProcessingEvent])

    mockResponses()

    when(mockMessageService.sendAggregateProcessingEventMessage(transferIdArgumentCaptor.capture(), eventArgumentCaptor.capture()))
      .thenReturn(SendMessageResponse.builder().build())

    val processor = new DataLoadProcessor(mockMessageService, mockConfig)
    val details = LoadCompletion(2, 1)
    val event = DataLoadProcessorEvent(SourceSystemEnum.SharePoint, transferId, Some(false), details)

    val result = processor.trigger(event, mockKeycloakToken).unsafeRunSync()
    result.transferId shouldBe transferId
    result.success shouldBe false

    transferIdArgumentCaptor.getValue shouldBe transferId
    eventArgumentCaptor.getValue.dataLoadErrors shouldBe true
    eventArgumentCaptor.getValue.metadataSourceObjectPrefix shouldBe s"$userId/sharepoint/$transferId/metadata"
    eventArgumentCaptor.getValue.metadataSourceBucket shouldBe "source-bucket"
  }

  "'trigger' function" should "not send aggregate processing SQS event message and return the correct result when client side errors present" in {
    val mockMessageService = mock[Messages]

    mockResponses()

    val processor = new DataLoadProcessor(mockMessageService, mockConfig)
    val details = LoadCompletion(2, 2, Set(LoadError("client side error message")))
    val event = DataLoadProcessorEvent(SourceSystemEnum.SharePoint, transferId, Some(false), details)

    val result = processor.trigger(event, mockKeycloakToken).unsafeRunSync()
    result.transferId shouldBe transferId
    result.success shouldBe false

    verify(mockMessageService, times(0)).sendAggregateProcessingEventMessage(any[UUID], any[AggregateProcessingEvent])
  }

  private def mockResponses(ignoreSiteNameBodies: String = ""): Unit = {
    when(mockConfig.s3).thenReturn(mockS3Config)
    when(mockConfig.transferConfiguration).thenReturn(mockTransferConfig)
    when(mockS3Config.metadataUploadBucketName).thenReturn("source-bucket")
    when(mockTransferConfig.ignoreSiteNameBodies).thenReturn(ignoreSiteNameBodies)
    when(mockKeycloakToken.userId).thenReturn(userId)
    when(mockKeycloakToken.transferringBody).thenReturn(Some("TDR-BODY2"))
  }
}
