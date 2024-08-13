package uk.gov.nationalarchives.tdr.transfer.service.api.model

import java.util.UUID

sealed trait LoadModel
sealed trait LoadDestinationModel
sealed trait MetadataPropertyModel

object LoadModel {
  case class MetadataPropertyDetails(propertyName: String, required: Boolean) extends MetadataPropertyModel
  case class AWSS3LoadDestination(bucketName: String, bucketKeyPrefix: String) extends LoadDestinationModel
  case class LoadDetails(
      transferId: UUID,
      recordsLoadDestination: AWSS3LoadDestination,
      metadataLoadDestination: AWSS3LoadDestination,
      metadataProperties: List[MetadataPropertyDetails] = List()
  ) extends LoadModel
}
