package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import graphql.codegen.GetConsignment.{getConsignment => gc}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.common.utils.authorisation.{Allow, ConsignmentAuthorisation, ConsignmentAuthorisationInput, Deny}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.{ErrorMessagePatterns, FeatureAccessControls, appConfig}
import uk.gov.nationalarchives.tdr.transfer.service.api.TransferServiceServer.backend
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.{AuthenticationError, AuthorisationError}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.{SharePoint, SourceSystem}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class Authorisation(
    authorisationModule: ConsignmentAuthorisation,
    featureAccessControls: FeatureAccessControls,
    errorMessagePatterns: ErrorMessagePatterns
) {
  private def sharePointUploadAccessDenied(sourceSystem: SourceSystem, isConnectorSharePointUser: Boolean): Boolean = {
    featureAccessControls.sharePointUpload && sourceSystem == SharePoint && !isConnectorSharePointUser
  }

  def validateUserConsignmentAccess(token: Token, transferId: UUID): IO[Unit] = {
    val input = ConsignmentAuthorisationInput(transferId, token)
    authorisationModule.hasAccess(input).flatMap {
      case Allow => IO.unit
      case Deny  =>
        val errorMessage = s"User ${token.userId} ${appConfig.errorMessagePatterns.consignmentAccessDenied}: $transferId"
        IO.raiseError(AuthorisationError(errorMessage))
    }
  }

  def endPointFeatureAccess(token: Token, sourceSystem: SourceSystem): IO[Unit] = {
    token match {
      case _ if sharePointUploadAccessDenied(sourceSystem, token.isConnectorSharePointUser) =>
        val errorMessage = s"User ${token.userId} ${errorMessagePatterns.sharePointUploadAccessDenied}"
        IO.raiseError(AuthorisationError(errorMessage))
      case _ => IO.unit
    }
  }
}

object Authorisation {
  private val apiUrl = appConfig.consignmentApi.url
  private val client = new GraphQLClient[gc.Data, gc.Variables](apiUrl)
  private val errorMessagePatterns: ErrorMessagePatterns = appConfig.errorMessagePatterns
  private val featureAccessControls: FeatureAccessControls = appConfig.featureAccessControls
  def apply() = new Authorisation(new ConsignmentAuthorisation(client), featureAccessControls, errorMessagePatterns)
}
