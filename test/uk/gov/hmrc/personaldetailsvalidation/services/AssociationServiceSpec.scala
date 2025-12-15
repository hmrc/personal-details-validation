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

import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.personaldetailsvalidation.mocks.repositories.MockAssociationRepository
import uk.gov.hmrc.personaldetailsvalidation.model.Association

import scala.concurrent.Future

class AssociationServiceSpec extends UnitSpec with CommonTestData with BeforeAndAfterEach {

  abstract class mockCryptoImpl extends Encrypter with Decrypter

  val mockCrypto: Encrypter & Decrypter = mock[mockCryptoImpl]

  val mockEncryption: Encryption = new Encryption(mock[Configuration]) {
    override lazy val crypto: Encrypter & Decrypter = mockCrypto
  }

  val associationService: AssociationService = new AssociationService(mockAssociationRepository, mockEncryption)

  override def beforeEach(): Unit = {
    reset(mockAssociationRepository)
    super.beforeEach()
  }

  "AssociationService" should {

    "insert an instance of Association into the association repository" in {

      val association: Association = Association(testCredId, testSessionId, testValidationId, testLastUpdated)

      MockAssociationRepository.insertRecord(mockAssociationRepository, association)(Future.successful(()))

      await(associationService.insertRecord(association)) shouldBe ()
    }

    "return an instance of an association given a credential and session identifier" in {

      val association: Association = Association(testCredId, testSessionId, testValidationId, testLastUpdated)

      when(mockCrypto.encrypt(PlainText(testCredId))).thenReturn(Crypted("foo"))
      when(mockCrypto.encrypt(PlainText(testSessionId))).thenReturn(Crypted("bar"))

      MockAssociationRepository.getRecord(mockAssociationRepository)(Some(association))

      await(associationService.getRecord(testCredId, testSessionId)) shouldBe Some(association)
    }
  }

}
