package uk.gov.hmrc.audit

import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class PlatformAnalyticsConnector {

  def sendEvent(event: GAEvent)(implicit headerCarrier: HeaderCarrier, executionContext: ExecutionContext): Unit = ???

}
