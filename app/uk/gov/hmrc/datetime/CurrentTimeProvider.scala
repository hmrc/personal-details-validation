package uk.gov.hmrc.datetime

import java.time.LocalDateTime
import javax.inject.Singleton

@Singleton
class CurrentTimeProvider extends (() => LocalDateTime) {
  def apply(): LocalDateTime = LocalDateTime.now()
}