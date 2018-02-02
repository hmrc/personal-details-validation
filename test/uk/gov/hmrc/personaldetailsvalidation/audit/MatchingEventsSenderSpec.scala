package uk.gov.hmrc.personaldetailsvalidation.audit

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.audit.{GAEvent, PlatformAnalyticsConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}

class MatchingEventsSenderSpec extends UnitSpec with MockFactory with ScalaFutures {

  "MatchingEventsSender" should {
    "send success MatchResultEvent" in new Setup {

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(MatchingGaEvent("success"), headerCarrier, executionContext)

      sender.sendMatchResultEvent(MatchSuccessful)
    }

    "send failure MatchResultEvent" in new Setup {

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(MatchingGaEvent("failed_matching"), headerCarrier, executionContext)

      sender.sendMatchResultEvent(MatchFailed)
    }

    "send technical error matching event" in new Setup {

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(MatchingGaEvent("technical_error_matching"), headerCarrier, executionContext)

      sender.sendMatchingErrorEvent
    }
  }

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val connector = mock[PlatformAnalyticsConnector]
    val sender = new MatchingEventsSender(connector)
  }

}
