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

import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.personaldetailsvalidation.AssociationRepository
import uk.gov.hmrc.personaldetailsvalidation.model.Association

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AssociationService @Inject()(associationRepository: AssociationRepository, encryption: Encryption){

  def insertRecord(association: Association): Future[Unit] = associationRepository.insertRecord(association)

  def getRecord(credentialId: String, sessionId: String): Future[Option[Association]] = {
    val credentialIdEncrypted: String = encryption.crypto.encrypt(PlainText(credentialId)).value
    val sessionIdEncrypted: String = encryption.crypto.encrypt(PlainText(sessionId)).value

    associationRepository.getRecord(credentialIdEncrypted, sessionIdEncrypted)
  }

}
