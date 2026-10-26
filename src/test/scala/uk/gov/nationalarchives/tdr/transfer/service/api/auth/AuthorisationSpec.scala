package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentMatchers.any
import uk.gov.nationalarchives.tdr.common.utils.authorisation.{Allow, ConsignmentAuthorisation, ConsignmentAuthorisationInput, Deny}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.{ConsignmentApi, ErrorMessagePatterns, FeatureAccessControls}
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation.AuthorisationInput
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.AuthorisationError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum
import uk.gov.nationalarchives.tdr.transfer.service.{ApplicationConfig, BaseSpec}

import java.util.UUID

class AuthorisationSpec extends BaseSpec {
  val authModule: ConsignmentAuthorisation = mock[ConsignmentAuthorisation]
  val mockConsignmentApi: ConsignmentApi = mock[ApplicationConfig.ConsignmentApi]
  val mockErrorMessagePatterns: ErrorMessagePatterns = mock[ErrorMessagePatterns]
  val mockFeatureAccessControls: FeatureAccessControls = mock[FeatureAccessControls]
  val authorisation: Authorisation = new Authorisation(authModule, mockFeatureAccessControls, mockErrorMessagePatterns)

  val token: Token = mock[Token]
  val consignmentId: UUID = UUID.fromString("6e3b76c4-1745-4467-8ac5-b4dd736e1b3e")
  val userId: UUID = UUID.fromString("4ab14990-ed63-4615-8336-56fbb9960300")

  when(mockConsignmentApi.url).thenReturn("someUrl")

  "endpointAuthorisation" should "succeed when user has access to consignment" in {
    when(token.userId).thenReturn(userId)
    when(authModule.hasAccess(any[ConsignmentAuthorisationInput])).thenReturn(IO(Allow))

    noException shouldBe thrownBy {
      authorisation.endpointAuthorisation(AuthorisationInput(token, Some(consignmentId), None)).unsafeRunSync()
    }
  }

  it should "raise AuthorisationError when user does not have access to consignment" in {
    when(token.userId).thenReturn(userId)
    when(authModule.hasAccess(any[ConsignmentAuthorisationInput])).thenReturn(IO(Deny))

    val ex = intercept[AuthorisationError] {
      authorisation.endpointAuthorisation(AuthorisationInput(token, Some(consignmentId), None)).unsafeRunSync()
    }

    ex.message should include(s"User $userId does not have access to consignment: $consignmentId")
    ex.getClass shouldBe classOf[AuthorisationError]
  }

  it should "succeed when sharepoint feature access enabled and user is connector sharepoint user" in {
    when(token.userId).thenReturn(userId)
    when(token.isConnectorSharePointUser).thenReturn(true)
    when(mockFeatureAccessControls.sharePointUpload).thenReturn(true)

    noException shouldBe thrownBy {
      authorisation.endpointAuthorisation(AuthorisationInput(token, None, Some(SourceSystemEnum.SharePoint))).unsafeRunSync()
    }
  }

  it should "raise authorisation error when sharepoint feature access control enabled and user is not connector sharepoint user" in {
    when(token.userId).thenReturn(userId)
    when(token.isConnectorSharePointUser).thenReturn(false)
    when(mockFeatureAccessControls.sharePointUpload).thenReturn(true)
    when(mockErrorMessagePatterns.sharePointUploadAccessDenied).thenReturn("does not have permission to upload from SharePoint")

    val ex = intercept[AuthorisationError] {
      authorisation.endpointAuthorisation(AuthorisationInput(token, None, Some(SourceSystemEnum.SharePoint))).unsafeRunSync()
    }

    ex.message should include(s"User $userId does not have permission to upload from SharePoint")
    ex.getClass shouldBe classOf[AuthorisationError]
  }

  it should "not raise authorisation error when sharepoint feature access control disabled and user is not connector sharepoint user" in {
    when(token.userId).thenReturn(userId)
    when(token.isConnectorSharePointUser).thenReturn(false)
    when(mockFeatureAccessControls.sharePointUpload).thenReturn(false)

    noException shouldBe thrownBy {
      authorisation.endpointAuthorisation(AuthorisationInput(token, None, Some(SourceSystemEnum.SharePoint))).unsafeRunSync()
    }
  }

  it should "not raise authorisation error when sharepoint feature access controlled enable but source system is not sharepoint" in {
    when(token.userId).thenReturn(userId)
    when(token.isConnectorSharePointUser).thenReturn(false)
    when(mockFeatureAccessControls.sharePointUpload).thenReturn(true)

    val nonSharePointSourceSystem = SourceSystemEnum.NetworkDrive

    noException shouldBe thrownBy {
      authorisation.endpointAuthorisation(AuthorisationInput(token, None, Some(nonSharePointSourceSystem))).unsafeRunSync()
    }
  }

  it should "raise an authorisation error when when sharepoint feature access controlled enable, use is sharepoint user, but does not have access to consignment" in {
    when(token.userId).thenReturn(userId)
    when(token.isConnectorSharePointUser).thenReturn(true)
    when(mockFeatureAccessControls.sharePointUpload).thenReturn(true)
    when(authModule.hasAccess(any[ConsignmentAuthorisationInput])).thenReturn(IO(Deny))

    val ex = intercept[AuthorisationError] {
      authorisation.endpointAuthorisation(AuthorisationInput(token, Some(consignmentId), Some(SourceSystemEnum.SharePoint))).unsafeRunSync()
    }

    ex.message should include(s"User $userId does not have access to consignment: $consignmentId")
    ex.getClass shouldBe classOf[AuthorisationError]
  }
}
