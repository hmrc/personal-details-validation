package uk.gov.hmrc.personaldetailsvalidation.audit

import uk.gov.hmrc.play.test.UnitSpec

class MatchingGaEventSpecs extends UnitSpec {

  "MatchingGaEvent" should {
    "have categor as sos_iv" in {
      MatchingGaEvent("foo").category shouldBe "sos_iv"
    }
    "have action as personal_detail_validation_result" in {
      MatchingGaEvent("foo").action shouldBe "personal_detail_validation_result"
    }
  }
}