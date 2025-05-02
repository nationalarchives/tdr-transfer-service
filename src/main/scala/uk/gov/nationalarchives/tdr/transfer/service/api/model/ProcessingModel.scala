package uk.gov.nationalarchives.tdr.transfer.service.api.model

import uk.gov.nationalarchives.tdr.transfer.service.api.model.ProcessingType.ProcessingTypeEnum.ProcessingType

object ProcessingModel {
  case class ProcessingError(processingType: ProcessingType, message: String)
  case class ProcessingCompletion(processingType: ProcessingType, processingErrors: Set[ProcessingError] = Set())
}
