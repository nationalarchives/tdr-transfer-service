package uk.gov.nationalarchives.tdr.transfer.service.services

import cats.effect.IO
import uk.gov.nationalarchives.tdr.transfer.service.api.errors.BackendError

trait BaseService[INPUT, RT] {
  def result(input: INPUT): IO[Either[BackendError, RT]]
}
