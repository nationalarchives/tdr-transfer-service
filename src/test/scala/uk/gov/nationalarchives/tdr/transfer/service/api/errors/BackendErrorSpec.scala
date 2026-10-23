package uk.gov.nationalarchives.tdr.transfer.service.api.errors

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.slf4j.{Logger => UnderlyingLogger}
import org.typelevel.log4cats.slf4j.Slf4jFactory
import uk.gov.nationalarchives.tdr.transfer.service.BaseSpec

class BackendErrorSpec extends BaseSpec {
  "handleErrorAsLeft" should "log the error message and return error as left" in {
    val mockLogger = mock[UnderlyingLogger]
    when(mockLogger.isErrorEnabled).thenReturn(true)

    val handler = new ErrorHandler()(Slf4jFactory.create[IO].getLoggerFromSlf4j(mockLogger))
    val expectedError = SeriesAssignmentError("Series assignment error message")
    val result = handler.handleErrorAsLeft(SeriesAssignmentError("Series assignment error message")).unsafeRunSync()

    result.value shouldBe expectedError
    verify(mockLogger, times(1)).isErrorEnabled
    verify(mockLogger).error(s"${expectedError.getClass.getSimpleName}: ${expectedError.message}")
  }
}
