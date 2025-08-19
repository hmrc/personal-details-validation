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

package support
import java.nio.charset.Charset
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.config.AppConfig
import uk.gov.hmrc.personaldetailsvalidation.{CitizenDetailsConnector, PdvRepository, PersonalDetailsValidator}
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.services.{AssociationService, PersonalDetailsValidatorService, RepoControlService}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, _}

trait UnitSpec extends AnyWordSpec with Matchers {

  implicit val timeout : Duration = 5 minutes

  def await[A](future: Future[A])(implicit timeout: Duration): A = Await.result(future, timeout)

  def status(of: Result): Int = of.header.status

  def status(of: Future[Result])(implicit timeout: Duration): Int = status(Await.result(of, timeout))

  def bodyOf(result: Result)(implicit mat: Materializer): String = {
    val bodyBytes: ByteString = await(result.body.consumeData)
    bodyBytes.decodeString(Charset.defaultCharset().name)
  }

  def bodyOf(resultF: Future[Result])(implicit mat: Materializer): Future[String] = resultF.map(bodyOf)

  def jsonBodyOf(result: Result)(implicit mat: Materializer): JsValue = Json.parse(bodyOf(result))

  def jsonBodyOf(resultF: Future[Result])(implicit mat: Materializer): Future[JsValue] = resultF.map(jsonBodyOf)

  val origin: Some[String] = Some("test")
  val credId: String = "cred-123"

  val sessionId: String = s"session-${UUID.randomUUID().toString}"
  val lastUpdated: LocalDateTime = LocalDateTime.now()

  val mockAuthConnector: AuthConnector                     = mock[AuthConnector]
  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
  val mockMatchingConnector: MatchingConnector             = mock[MatchingConnector]

  val mockAuditDataFactory: AuditDataEventFactory = mock[AuditDataEventFactory]
  val mockMatchingAuditConnector: AuditConnector  = mock[AuditConnector]

  val mockAppConfig: AppConfig = mock[AppConfig]

  val mockRepoControlService: RepoControlService = mock[RepoControlService]
  val mockPDVService: PersonalDetailsValidatorService = mock[PersonalDetailsValidatorService]
  val mockAssociationService: AssociationService = mock[AssociationService]
  val mockPersonalDetailsValidatorService: PersonalDetailsValidatorService = mock[PersonalDetailsValidatorService]

  val mockPdvRepository: PdvRepository = mock[PdvRepository]

  val mockValidator: PersonalDetailsValidator = mock[PersonalDetailsValidator]

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

}
