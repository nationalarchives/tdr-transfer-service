package uk.gov.nationalarchives.tdr.transfer.service.services.notifications

import software.amazon.awssdk.services.sns.SnsClient
import uk.gov.nationalarchives.aws.utils.sns.SNSClients.sns
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.Notifications.UploadEvent
import uk.gov.nationalarchives.tdr.transfer.service.{ApplicationConfig, BaseSpec}

class NotificationsSpec extends BaseSpec {
  "'sendUploadEventUserEmail'" should "send the an upload event message to the correct sns topic" in {
    val mockSnsConfig = mock[ApplicationConfig.Sns]
    val mockSnsUtils = mock[SNSUtils]
    when(mockSnsConfig.userEmailSnsTopicArn).thenReturn("user-email-sns-topic")
    val service = new Notifications(mockSnsUtils, mockSnsConfig)
    val event = UploadEvent("transferring body name", "consignment reference", "consignment id", "user id", "user@email", "some status")
    val expectedMessageString = """{
                                  |  "transferringBodyName" : "transferring body name",
                                  |  "consignmentReference" : "consignment reference",
                                  |  "consignmentId" : "consignment id",
                                  |  "userId" : "user id",
                                  |  "userEmail" : "user@email",
                                  |  "status" : "some status"
                                  |}""".stripMargin

    service.sendUploadEventUserEmail(event)
    verify(mockSnsUtils).publish(expectedMessageString, "user-email-sns-topic")
  }
}
