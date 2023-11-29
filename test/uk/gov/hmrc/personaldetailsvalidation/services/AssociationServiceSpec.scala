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

import org.scalamock.scalatest.MockFactory

import uk.gov.hmrc.personaldetailsvalidation.AssociationRepository
import uk.gov.hmrc.personaldetailsvalidation.model.Association

import support.UnitSpec

import scala.concurrent.Future

import java.time.LocalDateTime
import java.util.UUID

class AssociationServiceSpec extends UnitSpec with MockFactory {

  "AssociationService" should {

    "insert an instance of Association into the association repository" in new Setup {

      val association: Association = Association(testCredId, testSessionId, testValidationId, testLastUpdated)

      (mockRepository.insertRecord(_: Association)).expects(association).returning(Future.successful(()))

      await(associationService.insertRecord(association)) shouldBe ()
    }

    "return an instance of an association given a credential and session identifier" in new Setup {

      val association: Association = Association(testCredId, testSessionId, testValidationId, testLastUpdated)

      (mockRepository.getRecord(_: String, _: String)).expects(testCredId, testSessionId).returning(Future.successful(Some(association)))

      await(associationService.getRecord(testCredId, testSessionId)) shouldBe Some(association)
    }
  }

  trait Setup {

    val testCredId: String = "cred-123"
    val testSessionId: String = s"session-${UUID.randomUUID().toString}"
    val testValidationId: String = UUID.randomUUID().toString
    val testLastUpdated: LocalDateTime = LocalDateTime.now()

    val mockRepository: AssociationRepository = mock[AssociationRepository]

    val associationService: AssociationService = new AssociationService(mockRepository)

  }

}
