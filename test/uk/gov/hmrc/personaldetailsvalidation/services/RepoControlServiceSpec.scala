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
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.support.wiremock.WiremockConfiguration.{wiremockHost, wiremockPort}

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class RepoControlServiceSpec extends UnitSpec with MockFactory with GuiceOneAppPerSuite{

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(Map("metrics.enabled" -> "false")).build()

  "RepoControlService" should {
    "insert into both databases if given valid credentials" in new Setup {

      (mockAssociationService.insertRecord(_: Association))
        .expects(
          where[Association] {
            (association: Association) =>
              association.credentialId == encryptedCredID &&
              association.sessionId == encryptedSessionID &&
              association.validationId == validationId
          }
        )
        .returning(Future.successful(()))

      (mockPDVService.insertRecord(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, *)
        .returning(EitherT.rightT[Future, Exception](Done))

      val result: EitherT[Future, Exception, Done] = repoControl.insertPDVAndAssociationRecord(
        personalDetailsValidation, Some(credId), headerCarrier)

      println(result)

    }
  }

  trait Setup {
    lazy val config: Map[String, String] = Map(
      s"microservice.services.bas-proxy.host" -> s"$wiremockHost",
      s"microservice.services.bas-proxy.port" -> s"$wiremockPort",
      s"microservice.services.auth.host" -> s"$wiremockHost",
      s"microservice.services.auth.port" -> s"$wiremockPort",
      "play.filters.csrf.header.bypassHeaders.X-Requested-With" -> "*",
      "play.filters.csrf.header.bypassHeaders.Csrf-Token" -> "nocheck"
    )

    implicit val encryption: Encryption = app.injector.instanceOf[Encryption]

    val mockPDVService: PersonalDetailsValidatorService = mock[PersonalDetailsValidatorService]
    val mockAssociationService: AssociationService = mock[AssociationService]
    val repoControl = new RepoControlService(mockPDVService, mockAssociationService)

    val personalDetailsValidation: PersonalDetailsValidation = personalDetailsValidationObjects.generateOne
    val credId: String = "cred-123"
    val sessionId: SessionId = SessionId(s"session-${UUID.randomUUID().toString}")
    val validationId: String = UUID.randomUUID().toString
    val lastUpdated: LocalDateTime = LocalDateTime.now()
    val encryptedCredID: String = encryption.crypto.encrypt(credId, "credentialId").value
    val encryptedSessionID: String = encryption.crypto.encrypt(sessionId.toString, "sessionID").value
    val association: Association = Association(encryptedCredID, encryptedSessionID, validationId, lastUpdated)
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier().copy(sessionId = Some(sessionId))

  }

}

