package uk.gov.nationalarchives.tdr.transfer.service.services.dataload

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.AddConsignment.addConsignment.AddConsignment
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetConsignmentSummary.getConsignmentSummary.{GetConsignment => consignmentSummary}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec
import uk.gov.nationalarchives.tdr.transfer.service.api.model.LoadModel.{AWSS3LoadDestination, LoadDetails}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.services.GraphQlApiService

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

class DataLoadInitiationSpec extends BaseSpec {
  private val mockToken = mock[Token]
  private val mockBearerAccessToken = mock[BearerAccessToken]
  private val consignmentId = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  private val userId = UUID.randomUUID()
  private val sharePointSourceSystem = SourceSystemEnum.SharePoint
  private val nonSharePointSourceSystem = SourceSystemEnum.HardDrive
  private val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  "'initiateConsignmentLoad'" should "create a consignment and return expected 'LoadDetails' object when no existing consignment" in {
    val addConsignmentResponse = AddConsignment(Some(consignmentId), None, "Consignment-Ref")
    val mockGraphQlApiService = mock[GraphQlApiService]

    when(mockGraphQlApiService.addConsignment(mockToken, nonSharePointSourceSystem)).thenReturn(IO(addConsignmentResponse))
    when(mockGraphQlApiService.startUpload(mockToken, consignmentId, None, None)).thenReturn(IO("response string"))
    when(mockToken.bearerAccessToken).thenReturn(mockBearerAccessToken)
    when(mockToken.bearerAccessToken.getValue).thenReturn("some value")
    when(mockToken.userId).thenReturn(userId)

    val expectedResult = LoadDetails(
      consignmentId,
      "Consignment-Ref",
      AWSS3LoadDestination("aws-region", "s3BucketNameRecordsArn", "s3BucketNameRecordsName", s"$userId/$nonSharePointSourceSystem/$consignmentId/records"),
      AWSS3LoadDestination("aws-region", "s3BucketNameMetadataArn", "s3BucketNameMetadataName", s"$userId/$nonSharePointSourceSystem/$consignmentId/metadata")
    )

    val service = new DataLoadInitiation(mockGraphQlApiService)
    val result = service.initiateConsignmentLoad(mockToken, nonSharePointSourceSystem).unsafeRunSync()
    result shouldBe expectedResult
    verify(mockGraphQlApiService, times(1)).addConsignment(mockToken, nonSharePointSourceSystem)
    verify(mockGraphQlApiService, times(1)).startUpload(mockToken, consignmentId, None, None)
  }

  "'initiateConsignmentLoad'" should "override 'include top level folder' for share point source system" in {
    val addConsignmentResponse = AddConsignment(Some(consignmentId), None, "Consignment-Ref")
    val mockGraphQlApiService = mock[GraphQlApiService]

    when(mockGraphQlApiService.addConsignment(mockToken, sharePointSourceSystem)).thenReturn(IO(addConsignmentResponse))
    when(mockGraphQlApiService.startUpload(mockToken, consignmentId, None, Some(true))).thenReturn(IO("response string"))
    when(mockToken.bearerAccessToken).thenReturn(mockBearerAccessToken)
    when(mockToken.bearerAccessToken.getValue).thenReturn("some value")
    when(mockToken.userId).thenReturn(userId)

    val expectedResult = LoadDetails(
      consignmentId,
      "Consignment-Ref",
      AWSS3LoadDestination("aws-region", "s3BucketNameRecordsArn", "s3BucketNameRecordsName", s"$userId/$sharePointSourceSystem/$consignmentId/records"),
      AWSS3LoadDestination("aws-region", "s3BucketNameMetadataArn", "s3BucketNameMetadataName", s"$userId/$sharePointSourceSystem/$consignmentId/metadata")
    )

    val service = new DataLoadInitiation(mockGraphQlApiService)
    val result = service.initiateConsignmentLoad(mockToken, sharePointSourceSystem).unsafeRunSync()
    result shouldBe expectedResult
    verify(mockGraphQlApiService, times(1)).addConsignment(mockToken, sharePointSourceSystem)
    verify(mockGraphQlApiService, times(1)).startUpload(mockToken, consignmentId, None, Some(true))
  }

  "'initiateConsignmentLoad'" should "not create existing consignment when consignment exists" in {
    val existingConsignmentSummary = consignmentSummary(Some("series-name"), Some("transferring-body-name"), 1, "existing-consignment-ref")
    val uploadStatus = ConsignmentStatuses(UUID.randomUUID(), consignmentId, "Upload", "InProgress", someDateTime, None)
    val mockGraphQlApiService = mock[GraphQlApiService]

    when(mockGraphQlApiService.existingConsignment(mockToken, consignmentId)).thenReturn(IO(existingConsignmentSummary))
    when(mockGraphQlApiService.consignmentState(mockToken, consignmentId)).thenReturn(IO(List(uploadStatus)))
    when(mockToken.bearerAccessToken).thenReturn(mockBearerAccessToken)
    when(mockToken.bearerAccessToken.getValue).thenReturn("some value")
    when(mockToken.userId).thenReturn(userId)

    val expectedResult = LoadDetails(
      consignmentId,
      "existing-consignment-ref",
      AWSS3LoadDestination("aws-region", "s3BucketNameRecordsArn", "s3BucketNameRecordsName", s"$userId/$sharePointSourceSystem/$consignmentId/records"),
      AWSS3LoadDestination("aws-region", "s3BucketNameMetadataArn", "s3BucketNameMetadataName", s"$userId/$sharePointSourceSystem/$consignmentId/metadata")
    )

    val service = new DataLoadInitiation(mockGraphQlApiService)
    val result = service.initiateConsignmentLoad(mockToken, sharePointSourceSystem, Some(consignmentId)).unsafeRunSync()
    result shouldBe expectedResult
    verify(mockGraphQlApiService, times(1)).consignmentState(mockToken, consignmentId)
    verify(mockGraphQlApiService, times(1)).existingConsignment(mockToken, consignmentId)
  }

  "'initiateConsignmentLoad'" should "throw an error if 'addConsignment' GraphQl service call fails" in {
    val mockGraphQlApiService = mock[GraphQlApiService]
    when(mockGraphQlApiService.addConsignment(mockToken, sharePointSourceSystem)).thenThrow(new RuntimeException("Error adding consignment"))

    val service = new DataLoadInitiation(mockGraphQlApiService)

    val exception = intercept[RuntimeException] {
      service.initiateConsignmentLoad(mockToken, sharePointSourceSystem).attempt.unsafeRunSync()
    }
    exception.getMessage shouldBe "Error adding consignment"
    verify(mockGraphQlApiService, times(1)).addConsignment(mockToken, sharePointSourceSystem)
    verify(mockGraphQlApiService, times(0)).startUpload(mockToken, consignmentId, None)
  }

  "'initiateConsignmentLoad'" should "throw an error if 'startUpload' GraphQl service call fails" in {
    val addConsignmentResponse = AddConsignment(Some(consignmentId), None, "Consignment-Ref")
    val mockGraphQlApiService = mock[GraphQlApiService]

    when(mockGraphQlApiService.addConsignment(mockToken, sharePointSourceSystem)).thenReturn(IO(addConsignmentResponse))
    when(mockGraphQlApiService.startUpload(mockToken, consignmentId, None, Some(true))).thenThrow(new RuntimeException("Error starting upload"))

    val service = new DataLoadInitiation(mockGraphQlApiService)
    val response = service.initiateConsignmentLoad(mockToken, sharePointSourceSystem).attempt.unsafeRunSync()

    response.isLeft should equal(true)
    response.left.value.getMessage should equal("Error starting upload")
    verify(mockGraphQlApiService, times(1)).addConsignment(mockToken, sharePointSourceSystem)
    verify(mockGraphQlApiService, times(1)).startUpload(mockToken, consignmentId, None, Some(true))
  }
}
