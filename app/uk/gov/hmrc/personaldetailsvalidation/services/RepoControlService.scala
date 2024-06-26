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

import org.apache.pekko.Done
import cats.data.EitherT
import play.api.Logging
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}

import java.time.LocalDateTime
import javax.inject.{Inject,Singleton}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.crypto.PlainText

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RepoControlService @Inject()(pdvService: PersonalDetailsValidatorService, associationService: AssociationService) extends Logging{

  def insertPDVAndAssociationRecord(personalDetailsValidation: PersonalDetailsValidation,
                                    opCredID: Option[String])
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext, encryption: Encryption): EitherT[Future, Exception, Done] = {
    (opCredID, hc.sessionId) match {
      case (_       , None               ) => logger.warn(s"adding to Association database rejected due to sessionID does not exist - sessionId ${hc.sessionId}")
      case (_       , Some(SessionId(""))) => logger.warn(s"adding to Association database rejected due to sessionID containing empty string - sessionId ${hc.sessionId}")
      case (None    , _                  ) => logger.info(s"adding to Association database rejected due to credID does not exist (user may not be logged in) - sessionId ${hc.sessionId}")
      case (Some(""), _                  ) => logger.warn(s"adding to Association database rejected due to credID containing empty string - sessionId ${hc.sessionId}")
      case (_       , _                  ) =>
        val encryptedCredID = encryption.crypto.encrypt(PlainText(opCredID.get)).value
        val encryptedSessionID = encryption.crypto.encrypt(PlainText(hc.sessionId.get.value)).value
        val validationId = personalDetailsValidation.id.toString
        logger.info(s"Inserting into association DB for - sessionId ${hc.sessionId}")
        associationService.insertRecord(Association(encryptedCredID, encryptedSessionID, validationId, LocalDateTime.now()))
    }
    pdvService.insertRecord(personalDetailsValidation)
  }

}
