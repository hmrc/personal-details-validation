package uk.gov.hmrc.contracts

import java.time.LocalDate
import com.itv.scalapact.ScalaPactForger
import com.itv.scalapact.ScalaPactForger.{bodyRegexRule, bodyTypeRule, forgePact, interaction}
import com.itv.scalapact.http._
import com.itv.scalapact.json._
import play.api.http.Status.UNAUTHORIZED
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnectorImpl
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsWithNino}
import uk.gov.hmrc.support.BaseIntegrationSpec
import uk.gov.hmrc.support.stubs.AuthenticatorStub

import scala.concurrent.ExecutionContext.Implicits.global

class AuthenticatorCSpec extends BaseIntegrationSpec {

  implicit val hc = HeaderCarrier()

  "Authenticator Service" should {
    "find a known person" in new Setup {
      val jimFerguson = PersonalDetailsWithNino("Jim", "Ferguson", LocalDate.parse("1948-04-23"), Nino("AA000003D"))
      val jimFergusonAsString = Json.toJson[PersonalDetails](jimFerguson).toString()
      AuthenticatorStub.expecting(jimFergusonAsString).respondWithOK()

      forgePact
        .between("personal-details-validation")
        .and("authenticator")
        .addInteraction(
          interaction
            .description("successfully match user")
            .uponReceiving(
              method = ScalaPactForger.POST,
              path = "/authenticator/match",
              query = None,
              headers = Map("Content-Type" -> "application/json"),
              body = Some(jimFergusonAsString),
              matchingRules = bodyTypeRule("firstName") ~>
                bodyTypeRule("lastName") ~>
                bodyRegexRule("dateOfBirth", "^\\d{4}-\\d{2}-\\d{2}$") ~>
                bodyRegexRule("nino", "^[A-CEGHJ-PR-TW-Z]{1}[A-CEGHJ-NPR-TW-Z]{1}[0-9]{6}[A-DFM]{0,1}$")
            )
            .willRespondWith(
              status = OK,
              headers = Map(),
              body = Some(jimFergusonAsString),
              matchingRules = bodyTypeRule("firstName") ~>
                bodyTypeRule("lastName") ~>
                bodyRegexRule("dateOfBirth", "^\\d{4}-\\d{2}-\\d{2}$") ~>
                bodyRegexRule("nino", "^[A-CEGHJ-PR-TW-Z]{1}[A-CEGHJ-NPR-TW-Z]{1}[0-9]{6}[A-DFM]{0,1}$")
            )
        )
        .runConsumerTest { _ =>
          authConnector.doMatch(jimFerguson).value.futureValue mustBe Right(MatchSuccessful(jimFerguson))
        }
    }

    "not find an unknown person" in new Setup {
      val almostJimFerguson = PersonalDetailsWithNino("Jim", "Ferguson", LocalDate.parse("1948-04-23"), Nino("BB000003D"))
      val almostJimFergusonAsString = Json.toJson[PersonalDetails](almostJimFerguson).toString()
      val failureReason = "CID returned no record"
      val responseBody = Json.obj("errors" -> failureReason)
      AuthenticatorStub.expecting(almostJimFergusonAsString).respondWith(UNAUTHORIZED, Some(responseBody))

      forgePact
        .between("personal-details-validation")
        .and("authenticator")
        .addInteraction(
          interaction
            .description("fail to find the user in CID")
            .uponReceiving(
              method = ScalaPactForger.POST,
              path = "/authenticator/match",
              query = None,
              headers = Map("Content-Type" -> "application/json"),
              body = Some(almostJimFergusonAsString),
              matchingRules = bodyTypeRule("firstName") ~>
                bodyTypeRule("lastName") ~>
                bodyRegexRule("dateOfBirth", "^\\d{4}-\\d{2}-\\d{2}$") ~>
                bodyRegexRule("nino", "^[A-CEGHJ-PR-TW-Z]{1}[A-CEGHJ-NPR-TW-Z]{1}[0-9]{6}[A-DFM]{0,1}$")
            )
            .willRespondWith(
              status = UNAUTHORIZED,
              headers = Map(),
              body = responseBody.toString
            )
        )
        .runConsumerTest { mockConfig =>
          authConnector.doMatch(almostJimFerguson).value.futureValue mustBe Right(MatchFailed(failureReason))
        }
    }
  }

  private trait Setup {
    implicit val request: Request[_] = FakeRequest()
    val authConnector = app.injector.instanceOf[MatchingConnectorImpl]
  }
}
