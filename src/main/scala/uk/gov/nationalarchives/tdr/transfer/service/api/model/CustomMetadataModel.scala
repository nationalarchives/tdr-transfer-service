package uk.gov.nationalarchives.tdr.transfer.service.api.model

import uk.gov.nationalarchives.tdr.transfer.service.api.model.MetadataDataType.MetadataDataTypeEnum.MetadataDataType

sealed trait CustomMetadataModel

object CustomMetadataModel {
  case class CustomPropertyDetails(name: String, description: Option[String] = None, dataType: Option[MetadataDataType] = None)
}
