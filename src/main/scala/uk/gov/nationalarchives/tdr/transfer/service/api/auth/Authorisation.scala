package uk.gov.nationalarchives.tdr.transfer.service.api.auth

import cats.effect.IO
import graphql.codegen.GetConsignment.{getConsignment => gc}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.common.utils.authorisation.{Allow, ConsignmentAuthorisation, ConsignmentAuthorisationInput, Deny}
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.{ErrorMessagePatterns, FeatureAccessControls, appConfig}
import uk.gov.nationalarchives.tdr.transfer.service.api.TransferServiceServer.backend
import uk.gov.nationalarchives.tdr.transfer.service.api.auth.Authorisation.AuthorisationInput
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.AuthorisationError
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction
import uk.gov.nationalarchives.tdr.transfer.service.api.model.Common.TransferFunction.{Load, TransferFunction, Errors}
import uk.gov.nationalarchives.tdr.transfer.service.api.model.SourceSystem.SourceSystemEnum.{SharePoint, SourceSystem}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class Authorisation(
    authorisationModule: ConsignmentAuthorisation,
    featureAccessControls: FeatureAccessControls,
    errorMessagePatterns: ErrorMessagePatterns
) {
  private def loadAccessDenied(sourceSystem: Option[SourceSystem], token: Token): Boolean = {
    sourceSystem match {
      case Some(v) if v == SharePoint => featureAccessControls.sharePointUpload || !token.isConnectorSharePointUser
      case _                          => true
    }
  }

  private def transferErrorsAccessDenied(): Boolean = {
    featureAccessControls.transferErrors
  }

  private def validateUserConsignmentAccess(token: Token, transferId: Option[UUID]): IO[Unit] =
    transferId.fold(IO.unit) { id =>
      authorisationModule
        .hasAccess(ConsignmentAuthorisationInput(id, token))
        .flatMap {
          case Allow => IO.unit
          case Deny  =>
            IO.raiseError(
              AuthorisationError(
                s"User ${token.userId} ${errorMessagePatterns.consignmentAccessDenied}: $id"
              )
            )
        }
    }

  private def featureAccess(token: Token, transferFunction: TransferFunction, sourceSystem: Option[SourceSystem]): IO[Unit] = {
    val errorMessage = s"User ${token.userId} ${errorMessagePatterns.functionAccessDenied}: $transferFunction"
    transferFunction match {
      case Load =>
        if (loadAccessDenied(sourceSystem, token)) { IO.raiseError(AuthorisationError(errorMessage)) }
        else IO.unit
      case Errors =>
        if (transferErrorsAccessDenied()) { IO.raiseError(AuthorisationError(errorMessage)) }
        else IO.unit
      case _ =>
        IO.raiseError(AuthorisationError(errorMessage))
    }
  }

  def endpointAuthorisation(input: AuthorisationInput): IO[Unit] = {
    val token = input.authenticatedToken
    featureAccess(token, input.transferFunction, input.sourceSystem) *> validateUserConsignmentAccess(token, input.transferId)
  }
}

object Authorisation {
  private val apiUrl = appConfig.consignmentApi.url
  private val client = new GraphQLClient[gc.Data, gc.Variables](apiUrl)
  private val errorMessagePatterns: ErrorMessagePatterns = appConfig.errorMessagePatterns
  private val featureAccessControls: FeatureAccessControls = appConfig.featureAccessControls

  case class AuthorisationInput(
      authenticatedToken: Token,
      transferId: Option[UUID],
      sourceSystem: Option[SourceSystem],
      transferFunction: TransferFunction
  )

  def apply() = new Authorisation(new ConsignmentAuthorisation(client), featureAccessControls, errorMessagePatterns)
}
