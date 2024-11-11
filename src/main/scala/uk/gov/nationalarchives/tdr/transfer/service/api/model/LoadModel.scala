package uk.gov.nationalarchives.tdr.transfer.service.api.model

import java.util.UUID

sealed trait LoadModel
sealed trait LoadDestinationModel
sealed trait MetadataPropertyModel

object LoadModel {
  case class CustomMetadataConfiguration(required: Boolean = false) extends MetadataPropertyModel
  case class MetadataPropertyDetails(propertyName: String, required: Boolean) extends MetadataPropertyModel
  case class DisplayMessage(viewName: String, message: String)
  case class TransferConfiguration(
      maxNumberRecords: Int,
      maxIndividualFileSizeMb: Int,
      maxTransferSizeMb: Int,
      disallowedFileExtensions: Set[String] = Set(),
      metadataPropertyDetails: Set[MetadataPropertyDetails] = Set(),
      customMetadataConfiguration: CustomMetadataConfiguration = CustomMetadataConfiguration(),
      display: Set[DisplayMessage] = Set()
  ) extends LoadModel
  case class AWSS3LoadDestination(awsRegion: String, bucketArn: String, bucketName: String, bucketKeyPrefix: String) extends LoadDestinationModel
  case class LoadDetails(
      transferId: UUID,
      recordsLoadDestination: AWSS3LoadDestination,
      metadataLoadDestination: AWSS3LoadDestination
  ) extends LoadModel

  case class LoadError(message: String)
  case class LoadCompletion(expectedNumberFiles: Int, loadedNumberFiles: Int, loadErrors: Option[Set[LoadError]] = None)
}
