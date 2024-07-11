package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import software.amazon.awssdk.services.sfn.model.{StartExecutionRequest, StartExecutionResponse}
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionClients.sfnAsyncClient
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.services.dataload.DataLoadProcessor.ExecutionInput

import java.util.UUID

class DataLoadProcessor {
  implicit val transferCompletedEventEncoder: Encoder[ExecutionInput] = deriveEncoder[ExecutionInput]

  private val sfnConfig = ApplicationConfig.appConfig.sfn

  def trigger(consignmentId: UUID, token: Token): IO[String] = {
    // Trigger data load step function
    val input = ExecutionInput(consignmentId, token.userId, "metadata-file-name")
    val startExecutionRequest = StartExecutionRequest
      .builder()
      .input(input.asJson.toString)
      .build()

    IO(sfnAsyncClient(sfnConfig.dataLoadEndpoint).startExecution(startExecutionRequest).get().executionArn())
  }
}

object DataLoadProcessor {
  case class ExecutionInput(consignmentId: UUID, userId: UUID, fileId: String, scanType: String = "metadata")
  def apply() = new DataLoadProcessor
}
