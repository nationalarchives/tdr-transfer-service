package uk.gov.nationalarchives.tdr.transfer.service.services.notifications

import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.notifications.EmailClient.{SendEmailInput, notifyClient}
import uk.gov.service.notify.NotificationClient

import scala.jdk.CollectionConverters._

class EmailClient(emailClient: NotificationClient) {
  def sendEmail(inputs: List[SendEmailInput]): Unit = {
    if (inputs.size > 3000) {
      throw new RuntimeException("Batch size too large")
    }
    inputs.foreach(i => {
      println(s"Sending email to: ${i.emailAddress}...")
      emailClient.sendEmail(i.templateId, i.emailAddress, i.personalisation.asJava, i.reference)
//      Ensure don't breach api request limit: 3000 per minute
      Thread.sleep(10000)
    })
  }
}

object EmailClient {
  private val emailClientConfig = ApplicationConfig.appConfig.emailClient
  private val notifyClient = new NotificationClient(
    emailClientConfig.genericTemplateId,
    emailClientConfig.url
  )

  case class SendEmailInput(
      templateId: String = emailClientConfig.genericTemplateId,
      emailAddress: String,
      personalisation: Map[String, Any],
      reference: String
  )

  def apply() = new EmailClient(notifyClient)
}
