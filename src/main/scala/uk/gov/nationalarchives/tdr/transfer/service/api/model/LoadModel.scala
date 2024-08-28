package uk.gov.nationalarchives.tdr.transfer.service.api.model

import java.util.UUID

sealed trait LoadModel
sealed trait LoadDestinationModel
sealed trait MetadataPropertyModel

object LoadModel {
  case class CustomMetadataConfiguration(required: Boolean = false) extends MetadataPropertyModel
  case class MetadataPropertyDetails(propertyName: String, required: Boolean) extends MetadataPropertyModel
  case class TransferConfiguration(
      metadataPropertyDetails: Set[MetadataPropertyDetails] = Set(),
      customMetadataConfiguration: CustomMetadataConfiguration = CustomMetadataConfiguration()
  )
  case class AWSS3LoadDestination(bucketName: String, bucketKeyPrefix: String) extends LoadDestinationModel
  case class LoadDetails(
      transferId: UUID,
      recordsLoadDestination: AWSS3LoadDestination,
      metadataLoadDestination: AWSS3LoadDestination,
      transferConfiguration: TransferConfiguration = TransferConfiguration()
  ) extends LoadModel
}
