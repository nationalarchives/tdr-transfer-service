package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentMatchers.any
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor4, TableFor6}
import uk.gov.nationalarchives.tdr.common.utils.authorisation.{Allow, AuthorisationResult, ConsignmentAuthorisation, ConsignmentAuthorisationInput, Deny}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.{ConsignmentApi, ErrorMessagePatterns, FeatureAccessControls}
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation.AuthorisationInput
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.AuthorisationError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction.{Errors, Load, TransferFunction}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.{SharePoint, SourceSystem}
import uk.gov.nationalarchives.tdr.transfer.service.{ApplicationConfig, BaseSpec}

import java.util.UUID

class AuthorisationSpec extends BaseSpec with TableDrivenPropertyChecks {
  val authModule: ConsignmentAuthorisation = mock[ConsignmentAuthorisation]
  val mockConsignmentApi: ConsignmentApi = mock[ApplicationConfig.ConsignmentApi]
  val mockErrorMessagePatterns: ErrorMessagePatterns = mock[ErrorMessagePatterns]
  val mockFeatureAccessControls: FeatureAccessControls = mock[FeatureAccessControls]
  val mockToken: Token = mock[Token]

  val authorisation: Authorisation = new Authorisation(authModule, mockFeatureAccessControls, mockErrorMessagePatterns)
  val consignmentId: UUID = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  val userId: UUID = UUID.fromString("4ab14990-ed63-4615-8336-56fbb9960300")

  when(mockConsignmentApi.url).thenReturn("someUrl")
  when(mockErrorMessagePatterns.consignmentAccessDenied).thenReturn("does not have access to consignment")
  when(mockErrorMessagePatterns.functionAccessDenied).thenReturn("access denied to transfer function")

  def expectedAccessDeniedError(transferFunction: TransferFunction) =
    s"User $userId access denied to transfer function: $transferFunction"
  val expectedConsignmentAccessDeniedError =
    s"User $userId does not have access to consignment: $consignmentId"

  val loadFunctionScenarios: TableFor6[Boolean, AuthorisationResult, Boolean, SourceSystem, Boolean, Option[String]] = Table(
    ("connector for sharepoint user", "consignment access", "sharepoint upload  fac", "source system", "error expected", "expected error message"),
    (true, Allow, false, SharePoint, false, None),
    (true, Allow, true, SharePoint, true, Some(expectedAccessDeniedError(Load))),
    (true, Deny, false, SharePoint, true, Some(expectedConsignmentAccessDeniedError)),
    (true, Deny, true, SharePoint, true, Some(expectedAccessDeniedError(Load))),
    (false, Allow, false, SharePoint, true, Some(expectedAccessDeniedError(Load))),
    (false, Allow, true, SharePoint, true, Some(expectedAccessDeniedError(Load))),
    (false, Deny, false, SharePoint, true, Some(expectedAccessDeniedError(Load))),
    (false, Deny, true, SharePoint, true, Some(expectedAccessDeniedError(Load)))
  )

  forAll(loadFunctionScenarios) {
    (
        isConnectorForSharePointUser,
        consignmentAccess,
        sharepointUploadFac,
        sourceSystem,
        errorExpected,
        expectedErrorMessage
    ) =>
      s"endpointAuthorisation with $sourceSystem" should
        s"provide correct response when: connector user $isConnectorForSharePointUser; consignment access: $consignmentAccess; SharePoint Upload FAC $sharepointUploadFac" in {
          when(mockToken.userId).thenReturn(userId)
          when(mockToken.isConnectorSharePointUser).thenReturn(isConnectorForSharePointUser)
          when(authModule.hasAccess(any[ConsignmentAuthorisationInput])).thenReturn(IO(consignmentAccess))
          when(mockFeatureAccessControls.sharePointUpload).thenReturn(sharepointUploadFac)

          if (errorExpected) {
            val ex = intercept[AuthorisationError] {
              authorisation.endpointAuthorisation(AuthorisationInput(mockToken, Some(consignmentId), Some(sourceSystem), Load)).unsafeRunSync()
            }
            ex.message should include(expectedErrorMessage.get)
            ex.getClass shouldBe classOf[AuthorisationError]
          } else {
            noException shouldBe thrownBy {
              authorisation.endpointAuthorisation(AuthorisationInput(mockToken, Some(consignmentId), Some(sourceSystem), Load)).unsafeRunSync()
            }
          }
        }
  }

  val transferErrorsScenarios: TableFor4[Boolean, AuthorisationResult, Boolean, Option[String]] = Table(
    ("transfer errors fac", "consignment access", "error expected", "expected error message"),
    (false, Allow, false, None),
    (false, Deny, true, Some(expectedConsignmentAccessDeniedError)),
    (true, Allow, true, Some(expectedAccessDeniedError(Errors))),
    (true, Deny, true, Some(expectedAccessDeniedError(Errors)))
  )

  forAll(transferErrorsScenarios) {
    (
        transferErrorsFac,
        consignmentAccess,
        errorExpected,
        expectedErrorMessage
    ) =>
      s"endpointAuthorisation" should
        s"provide correct response when: transfer errors fac $transferErrorsFac; consignment access: $consignmentAccess" in {
          when(mockToken.userId).thenReturn(userId)
          when(authModule.hasAccess(any[ConsignmentAuthorisationInput])).thenReturn(IO(consignmentAccess))
          when(mockFeatureAccessControls.transferErrors).thenReturn(transferErrorsFac)

          if (errorExpected) {
            val ex = intercept[AuthorisationError] {
              authorisation.endpointAuthorisation(AuthorisationInput(mockToken, Some(consignmentId), None, Errors)).unsafeRunSync()
            }
            ex.message should include(expectedErrorMessage.get)
            ex.getClass shouldBe classOf[AuthorisationError]
          } else {
            noException shouldBe thrownBy {
              authorisation.endpointAuthorisation(AuthorisationInput(mockToken, Some(consignmentId), None, Errors)).unsafeRunSync()
            }
          }
        }
  }
}
