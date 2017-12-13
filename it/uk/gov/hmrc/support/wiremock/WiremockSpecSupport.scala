package uk.gov.hmrc.support.wiremock

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait WiremockSpecSupport extends BeforeAndAfterEach with BeforeAndAfterAll with WiremockHelper {

  suite: Suite =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    resetWiremock()
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
