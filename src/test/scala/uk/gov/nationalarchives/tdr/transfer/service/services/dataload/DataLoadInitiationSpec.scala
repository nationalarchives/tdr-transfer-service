package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.AddConsignment.addConsignment.AddConsignment
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.util.UUID

class DataLoadInitiationSpec extends BaseSpec {
  private val mockToken = mock[Token]
  private val mockBearerAccessToken = mock[BearerAccessToken]
  private val consignmentId = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  private val userId = UUID.randomUUID()
  private val sourceSystem = SourceSystemEnum.SharePoint

  "'initiateConsignmentLoad'" should "create a consignment and return expected 'LoadDetails' object" in {
    val addConsignmentResponse = AddConsignment(Some(consignmentId), None, "Consignment-Ref")
    val mockGraphQlApiService = mock[GraphQlApiService]

    when(mockGraphQlApiService.addConsignment(mockToken)).thenReturn(IO(addConsignmentResponse))
    when(mockGraphQlApiService.startUpload(mockToken, consignmentId)).thenReturn(IO("response string"))
    when(mockToken.bearerAccessToken).thenReturn(mockBearerAccessToken)
    when(mockToken.bearerAccessToken.getValue).thenReturn("some value")
    when(mockToken.userId).thenReturn(userId)

    val expectedResult = LoadDetails(
      consignmentId,
      "Consignment-Ref",
      AWSS3LoadDestination("aws-region", "s3BucketNameRecordsArn", "s3BucketNameRecordsName", s"$userId/$sourceSystem/$consignmentId/records"),
      AWSS3LoadDestination("aws-region", "s3BucketNameMetadataArn", "s3BucketNameMetadataName", s"$userId/$sourceSystem/$consignmentId/metadata")
    )

    val service = new DataLoadInitiation(mockGraphQlApiService)
    val result = service.initiateConsignmentLoad(mockToken, sourceSystem).unsafeRunSync()
    result shouldBe expectedResult
    verify(mockGraphQlApiService, times(1)).addConsignment(mockToken)
    verify(mockGraphQlApiService, times(1)).startUpload(mockToken, consignmentId, None)
  }

  "'initiateConsignmentLoad'" should "throw an error if 'addConsignment' GraphQl service call fails" in {
    val mockGraphQlApiService = mock[GraphQlApiService]
    when(mockGraphQlApiService.addConsignment(mockToken)).thenThrow(new RuntimeException("Error adding consignment"))

    val service = new DataLoadInitiation(mockGraphQlApiService)

    val exception = intercept[RuntimeException] {
      service.initiateConsignmentLoad(mockToken, sourceSystem).attempt.unsafeRunSync()
    }
    exception.getMessage shouldBe "Error adding consignment"
    verify(mockGraphQlApiService, times(1)).addConsignment(mockToken)
    verify(mockGraphQlApiService, times(0)).startUpload(mockToken, consignmentId, None)
  }

  "'initiateConsignmentLoad'" should "throw an error if 'startUpload' GraphQl service call fails" in {
    val addConsignmentResponse = AddConsignment(Some(consignmentId), None, "Consignment-Ref")
    val mockGraphQlApiService = mock[GraphQlApiService]

    when(mockGraphQlApiService.addConsignment(mockToken)).thenReturn(IO(addConsignmentResponse))
    when(mockGraphQlApiService.startUpload(mockToken, consignmentId)).thenThrow(new RuntimeException("Error starting upload"))

    val service = new DataLoadInitiation(mockGraphQlApiService)
    val response = service.initiateConsignmentLoad(mockToken, sourceSystem).attempt.unsafeRunSync()

    response.isLeft should equal(true)
    response.left.value.getMessage should equal("Error starting upload")
    verify(mockGraphQlApiService, times(1)).addConsignment(mockToken)
    verify(mockGraphQlApiService, times(1)).startUpload(mockToken, consignmentId, None)
  }
}
