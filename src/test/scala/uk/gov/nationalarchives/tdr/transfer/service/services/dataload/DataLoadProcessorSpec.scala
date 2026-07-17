package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor7}
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.{ClientChecksType, UploadType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues._
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{LoadCompletion, LoadCompletionResponse, LoadError}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadProcessor.DataLoadProcessorEvent
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Messages.AggregateProcessingEvent
import uk.gov.nationalarchives.tdr.transfer.service.{ApplicationConfig, BaseSpec}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

class DataLoadProcessorSpec extends BaseSpec with TableDrivenPropertyChecks {
  private val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
  val userId: UUID = UUID.randomUUID()
  val transferId: UUID = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  val mockKeycloakToken: Token = mock[Token]
  val mockConfig: ApplicationConfig.Configuration = mock[ApplicationConfig.Configuration]
  val mockS3Config: ApplicationConfig.S3 = mock[ApplicationConfig.S3]
  val mockTransferConfig: ApplicationConfig.TransferConfiguration = mock[ApplicationConfig.TransferConfiguration]
  val mockGraphQlApiService: GraphQlApiService = mock[GraphQlApiService]

  val noErrors = LoadCompletion(2, 2)
  val dataLoadErrorsOnly = LoadCompletion(2, 1)
  val clientSideErrorsOnly = LoadCompletion(2, 2, Set(LoadError("client side error")))
  val allErrors = LoadCompletion(2, 1, Set(LoadError("client side error")))

  private val correctTransferStatuses = List(
    ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, InProgressValue.value, someDateTime, None),
    ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, InProgressValue.value, someDateTime, None)
  )

  private val incorrectUploadStatus = List(
    ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, CompletedValue.value, someDateTime, None),
    ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, InProgressValue.value, someDateTime, None)
  )

  private val incorrectClientSideChecksStatus = List(
    ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, InProgressValue.value, someDateTime, None),
    ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, CompletedWithIssuesValue.value, someDateTime, None)
  )

  private val incorrectTransferStatuses = List(
    ConsignmentStatuses(UUID.randomUUID(), transferId, UploadType.id, CompletedValue.value, someDateTime, None),
    ConsignmentStatuses(UUID.randomUUID(), transferId, ClientChecksType.id, CompletedWithIssuesValue.value, someDateTime, None)
  )

  val successResponse = LoadCompletionResponse(transferId, success = true)
  val noSuccessResponse = LoadCompletionResponse(transferId, success = false)

  val scenarios: TableFor7[String, List[ConsignmentStatuses], LoadCompletion, Boolean, LoadCompletionResponse, StatusValue, Int] = Table(
    (
      "Scenario",
      "Transfer State",
      "Load Completion Details",
      "Expected Data Load Errors",
      "Expected Load Completion Response",
      "Updated Upload Status Value",
      "Expected Number of SNS Messages"
    ),
    ("transfer state correct and no errors", correctTransferStatuses, noErrors, false, successResponse, CompletedValue, 1),
    ("transfer state correct with data load errors", correctTransferStatuses, dataLoadErrorsOnly, true, noSuccessResponse, FailedValue, 1),
    ("transfer state correct with client side errors", correctTransferStatuses, clientSideErrorsOnly, false, noSuccessResponse, FailedValue, 0),
    ("transfer state correct with client side and data load errors", correctTransferStatuses, allErrors, true, noSuccessResponse, FailedValue, 0),
    ("Upload state not correct and no errors", incorrectUploadStatus, noErrors, true, noSuccessResponse, FailedValue, 1),
    ("Upload state not correct with client side errors", incorrectUploadStatus, clientSideErrorsOnly, true, noSuccessResponse, FailedValue, 0),
    ("Upload state not correct with data load errors", incorrectUploadStatus, dataLoadErrorsOnly, true, noSuccessResponse, FailedValue, 1),
    ("Upload state not correct with client side errors and data load errors", incorrectUploadStatus, allErrors, true, noSuccessResponse, FailedValue, 0),
    ("Client Side Checks state not in progress and no errors", incorrectClientSideChecksStatus, noErrors, true, noSuccessResponse, FailedValue, 1),
    ("Client Side Checks state not in progress with client side errors", incorrectClientSideChecksStatus, clientSideErrorsOnly, true, noSuccessResponse, FailedValue, 0),
    ("Client Side Checks state not in progress with data load errors", incorrectClientSideChecksStatus, dataLoadErrorsOnly, true, noSuccessResponse, FailedValue, 1),
    ("Client Side Checks state not in progress with client side and data load errors", incorrectClientSideChecksStatus, allErrors, true, noSuccessResponse, FailedValue, 0),
    ("transfer state incorrect and no errors", incorrectTransferStatuses, noErrors, true, noSuccessResponse, FailedValue, 1),
    ("transfer state incorrect with data load errors", incorrectTransferStatuses, dataLoadErrorsOnly, true, noSuccessResponse, FailedValue, 1),
    ("transfer state incorrect with client side errors", incorrectTransferStatuses, clientSideErrorsOnly, true, noSuccessResponse, FailedValue, 0),
    ("transfer state incorrect with client side and data load errors", incorrectTransferStatuses, allErrors, true, noSuccessResponse, FailedValue, 0)
  )

  forAll(scenarios) { (scenario, transferStatuses, loadCompletionDetails, expectedDataLoadErrors, expectedLoadResponse, expectedUploadStatusValue, expectedNumberSnsMessages) =>
    {
      "'trigger' function" should s"send correct aggregate processing SQS event message and return the correct result for $scenario" in {
        val mockMessageService = mock[Messages]
        val transferIdArgumentCaptor: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
        val eventArgumentCaptor: ArgumentCaptor[AggregateProcessingEvent] = ArgumentCaptor.forClass(classOf[AggregateProcessingEvent])

        mockResponses()
        when(mockMessageService.sendAggregateProcessingEventMessage(transferIdArgumentCaptor.capture(), eventArgumentCaptor.capture()))
          .thenReturn(SendMessageResponse.builder().build())
        when(mockGraphQlApiService.consignmentState(mockKeycloakToken, transferId)).thenReturn(IO(transferStatuses))
        when(mockGraphQlApiService.updateConsignmentStatus(mockKeycloakToken, transferId, UploadType, expectedUploadStatusValue)).thenReturn(IO(Some(1)))

        val processor = new DataLoadProcessor(mockMessageService, mockConfig, mockGraphQlApiService)
        val event = DataLoadProcessorEvent(SourceSystemEnum.SharePoint, transferId, Some(false), loadCompletionDetails)

        val result = processor.trigger(event, mockKeycloakToken).unsafeRunSync()
        result.transferId shouldBe expectedLoadResponse.transferId
        result.success shouldBe expectedLoadResponse.success

        verify(mockMessageService, times(expectedNumberSnsMessages)).sendAggregateProcessingEventMessage(any[UUID], any[AggregateProcessingEvent])

        if (expectedNumberSnsMessages > 0) {
          transferIdArgumentCaptor.getValue shouldBe transferId
          eventArgumentCaptor.getValue.dataLoadErrors shouldBe expectedDataLoadErrors
          eventArgumentCaptor.getValue.metadataSourceObjectPrefix shouldBe s"$userId/sharepoint/$transferId/metadata"
          eventArgumentCaptor.getValue.metadataSourceBucket shouldBe "source-bucket"
          eventArgumentCaptor.getValue.ignoreSiteName shouldBe false
        }
      }
    }
  }

  "'trigger' function" should "send aggregate processing SQS event message and return the correct result when ignore site name" in {
    val mockMessageService = mock[Messages]
    val transferIdArgumentCaptor: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
    val eventArgumentCaptor: ArgumentCaptor[AggregateProcessingEvent] = ArgumentCaptor.forClass(classOf[AggregateProcessingEvent])

    mockResponses(ignoreSiteNameBodies = "TDR-BODY1;TDR-BODY2")

    when(mockMessageService.sendAggregateProcessingEventMessage(transferIdArgumentCaptor.capture(), eventArgumentCaptor.capture()))
      .thenReturn(SendMessageResponse.builder().build())
    when(mockGraphQlApiService.consignmentState(mockKeycloakToken, transferId)).thenReturn(IO(correctTransferStatuses))
    when(mockGraphQlApiService.updateConsignmentStatus(mockKeycloakToken, transferId, UploadType, CompletedValue)).thenReturn(IO(Some(1)))

    val processor = new DataLoadProcessor(mockMessageService, mockConfig, mockGraphQlApiService)
    val event = DataLoadProcessorEvent(SourceSystemEnum.SharePoint, transferId, Some(false), noErrors)

    val result = processor.trigger(event, mockKeycloakToken).unsafeRunSync()
    result.transferId shouldBe transferId
    result.success shouldBe true

    transferIdArgumentCaptor.getValue shouldBe transferId
    eventArgumentCaptor.getValue.dataLoadErrors shouldBe false
    eventArgumentCaptor.getValue.metadataSourceObjectPrefix shouldBe s"$userId/sharepoint/$transferId/metadata"
    eventArgumentCaptor.getValue.metadataSourceBucket shouldBe "source-bucket"
    eventArgumentCaptor.getValue.ignoreSiteName shouldBe true
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
