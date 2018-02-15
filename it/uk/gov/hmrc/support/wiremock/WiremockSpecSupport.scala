package uk.gov.hmrc.support.wiremock

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import uk.gov.hmrc.support.stubs.AuditEventStubs.stubAuditEvent
import uk.gov.hmrc.support.stubs.PlatformAnalyticsStub.stubGAMatchEvent

trait WiremockSpecSupport extends BeforeAndAfterEach with BeforeAndAfterAll with WiremockHelper {

  suite: Suite =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    resetWiremock()
    stubGAMatchEvent()
    stubAuditEvent()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }
}
