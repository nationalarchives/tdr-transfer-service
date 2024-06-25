package uk.gov.nationalarchives.tdr.transfer.service.api.model

import java.util.UUID

sealed trait LoadModel
sealed trait LoadDestinationModel

object LoadModel {
  case class AWSS3LoadDestination(bucketName: String, bucketKey: String) extends LoadDestinationModel
  case class LoadDetails(consignmentId: UUID, recordsLoadDestination: AWSS3LoadDestination, metadataLoadDestination: AWSS3LoadDestination) extends LoadModel
}
