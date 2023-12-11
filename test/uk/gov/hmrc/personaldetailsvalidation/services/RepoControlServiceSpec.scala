/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.Done
import cats.data.EitherT
import generators.ObjectGenerators.personalDetailsValidationObjects
import org.scalamock.scalatest.MockFactory
import support.UnitSpec
import uk.gov.hmrc.personaldetailsvalidation.model.{Association, PersonalDetailsValidation}
import generators.Generators.Implicits._
import org.scalamock.matchers.ArgCapture.CaptureOne
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.crypto.PlainText

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class RepoControlServiceSpec extends UnitSpec with MockFactory with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(Map("metrics.enabled" -> "false")).build()

  "RepoControlService" should {
    "insert into both databases if given valid credentials" in new Setup {

      val capturedAssociation: CaptureOne[Association] = CaptureOne[Association]()

      mockAssociationService.insertRecord _ expects capture(capturedAssociation) returning Future.successful(())

      (mockPDVService.insertRecord(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, *)
        .returning(EitherT.rightT[Future, Exception](Done))

      val result: EitherT[Future, Exception, Done] = repoControl.insertPDVAndAssociationRecord(
        personalDetailsValidation, Some(credId), headerCarrier)

      await(result.value) shouldBe Right(Done)

      capturedAssociation.value.validationId shouldBe personalDetailsValidation.id.toString
    }
  }

  trait Setup {

    val sessionId: String = s"session-${UUID.randomUUID().toString}"

    implicit val headerCarrier: HeaderCarrier = new HeaderCarrier(sessionId = Some(SessionId(sessionId)))
    implicit val encryption: Encryption = app.injector.instanceOf[Encryption]

    val mockPDVService: PersonalDetailsValidatorService = mock[PersonalDetailsValidatorService]
    val mockAssociationService: AssociationService = mock[AssociationService]
    val repoControl = new RepoControlService(mockPDVService, mockAssociationService)

    val personalDetailsValidation: PersonalDetailsValidation = personalDetailsValidationObjects.generateOne
    val credId: String = "cred-123"
    val lastUpdated: LocalDateTime = LocalDateTime.now()
    val encryptedCredID: String = encryption.crypto.encrypt(PlainText(credId)).value
    val encryptedSessionID: String = encryption.crypto.encrypt(PlainText(sessionId)).value
    val association: Association = Association(encryptedCredID, encryptedSessionID, personalDetailsValidation.id.toString, lastUpdated)
  }

}


