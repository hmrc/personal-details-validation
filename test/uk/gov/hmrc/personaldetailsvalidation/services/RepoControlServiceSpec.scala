/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.personaldetailsvalidation.services

import cats.data.EitherT
import ch.qos.logback.classic.Level
import org.apache.pekko.Done
import org.mockito.MockitoSugar.reset
import org.scalatest.concurrent.Eventually
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, LoneElement}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Logger}
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.personaldetailsvalidation.mocks.services.{MockAssociationService, MockPdvService}
import uk.gov.hmrc.personaldetailsvalidation.model.Association
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RepoControlServiceSpec extends
  AnyWordSpec
  with UnitSpec
  with GuiceOneAppPerSuite
  with LoneElement
  with Eventually
  with LogCapturing
  with CommonTestData
  with BeforeAndAfterEach {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(Map("metrics.enabled" -> "false")).build()

  implicit val encryption: Encryption = app.injector.instanceOf[Encryption]

  val repoControl: RepoControlService = new RepoControlService(
    mockPDVService,
    mockAssociationService
  )

  val encryptedCredID: String = encryption.crypto.encrypt(PlainText(testCredId)).value
  val encryptedSessionID: String = encryption.crypto.encrypt(PlainText(testValidationId)).value

  val association: Association = Association(
    encryptedCredID,
    encryptedSessionID,
    personalDetailsValidation.id.toString,
    testLastUpdated)

  override def beforeEach(): Unit = {
    reset(mockPDVService, mockAssociationService)
    super.beforeEach()
  }

  "RepoControlService" should {
    "insert into both databases if given valid credentials" in {

      implicit val headerCarrier: HeaderCarrier = new HeaderCarrier(sessionId = Some(SessionId(testValidationId)))

      val association = Association(
        credentialId = testCredId,
        sessionId = testValidationId,
        validationId = personalDetailsValidation.id.toString,
        lastUpdated = testLastUpdated
      )

      MockAssociationService.insertRecord(mockAssociationService, association)
      MockPdvService.insertRecord(mockPDVService, personalDetailsValidation)(Done)

      val result = await(repoControl.insertPDVAndAssociationRecord(
        personalDetailsValidation, Some(testCredId)).value)

      result shouldBe Right(Done)

      association.validationId shouldBe personalDetailsValidation.id.toString
    }

    "only insert into the PDV database if sessionID is none" in {

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>

        implicit val hc: HeaderCarrier = new HeaderCarrier(sessionId = None)

        MockPdvService.insertRecord(mockPDVService, personalDetailsValidation)(Done)

        val result = await(repoControl.insertPDVAndAssociationRecord(
          personalDetailsValidation, Some(testCredId)).value)

        result shouldBe Right(Done)

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage shouldBe "adding to Association database rejected due to sessionID does not exist - sessionId None"
        }
      }
    }

    "only insert into the PDV database if sessionID is an empty string" in {

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>

        implicit val hc: HeaderCarrier = new HeaderCarrier(sessionId = Some(SessionId("")))

        MockPdvService.insertRecord(mockPDVService, personalDetailsValidation)(Done)

        val result = await(repoControl.insertPDVAndAssociationRecord(
          personalDetailsValidation, Some(testCredId)).value)

        result shouldBe Right(Done)

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage shouldBe s"""adding to Association database rejected due to sessionID containing empty string - sessionId ${Some(SessionId(""))}"""
        }
      }
    }

    "only insert into the PDV database if credID is None" in {

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>

        implicit val headerCarrier: HeaderCarrier = new HeaderCarrier(sessionId = Some(SessionId(testValidationId)))

        MockPdvService.insertRecord(mockPDVService, personalDetailsValidation)(Done)

        val result = await(repoControl.insertPDVAndAssociationRecord(
          personalDetailsValidation, None).value)

        result shouldBe Right(Done)

        eventually {
          logEvents
            .filter(_.getLevel == Level.INFO)
            .loneElement
            .getMessage shouldBe s"adding to Association database rejected due to credID does not exist (user may not be logged in) - sessionId ${headerCarrier.sessionId}"
        }
      }
    }

    "only insert into the PDV database if credID is an empty string" in {

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>

        implicit val headerCarrier: HeaderCarrier = new HeaderCarrier(sessionId = Some(SessionId(testValidationId)))

        MockPdvService.insertRecord(mockPDVService, personalDetailsValidation)(Done)

        val result: EitherT[Future, Exception, Done] = repoControl.insertPDVAndAssociationRecord(
          personalDetailsValidation, Some(""))

        await(result.value) shouldBe Right(Done)

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage shouldBe s"adding to Association database rejected due to credID containing empty string - sessionId ${headerCarrier.sessionId}"
        }
      }
    }

  }

}