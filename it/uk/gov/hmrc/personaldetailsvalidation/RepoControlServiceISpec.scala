package uk.gov.hmrc.personaldetailsvalidation

import akka.Done
import cats.data.EitherT
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsWithNino, SuccessfulPersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.personaldetailsvalidation.services.{AssociationService, Encryption, PersonalDetailsValidatorService, RepoControlService}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.crypto.PlainText

class RepoControlServiceISpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterEach
  with GuiceOneServerPerSuite
  with ScalaFutures {

  val config: PersonalDetailsValidationMongoRepositoryConfig = app.injector.instanceOf[PersonalDetailsValidationMongoRepositoryConfig]
  val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  lazy val associationRepository: AssociationMongoRepository = app.injector.instanceOf[AssociationMongoRepository]
  lazy val pdvRepository: PdvRepository = app.injector.instanceOf[PdvRepository]

  "RepoControlService" should {
    "save an instant of Association and personal details" in new Setup {

      val timeBeforeTest: LocalDateTime = LocalDateTime.now

      val result: EitherT[Future, Exception, Done] = repoControlService.insertPDVAndAssociationRecord(personalDetailsValidation,Some(testCredId))(headerCarrier, ec, encryption)

      result.value.futureValue

      val timeAfterTest: LocalDateTime = LocalDateTime.now

      associationService.getRecord(encryptedCredId, encryptedSessionID).futureValue match {
        case Some(retrieved) =>
          retrieved.credentialId shouldBe encryptedCredId
          retrieved.sessionId shouldBe encryptedSessionID
          retrieved.validationId shouldBe testValidationId.toString
          retrieved.lastUpdated.isAfter(timeBeforeTest) shouldBe true
          retrieved.lastUpdated.isBefore(timeAfterTest) shouldBe true

        case None =>
          fail("Expected instance of association was not retrieved")
      }

      pdvService.getRecord(ValidationId(testValidationId))(ec).futureValue match {
        case Some(retrieved) =>
          retrieved.id.value.toString shouldBe testValidationId.toString
        case None => fail("Expected instance of personalDetails was not retrieved")
      }
    }
  }

  trait Setup {

    val testSessionId: String = s"session-${UUID.randomUUID().toString}"
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(testSessionId)))

    val dateFormat: String = "yyyy-MM-dd HH:mm:ss.SSS"
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat)
    val testCredId: String = "cred-123"
    val testValidationId: UUID = UUID.randomUUID()
    val testLastUpdated: LocalDateTime = LocalDateTime.parse("2023-11-01 12:00:00.000", formatter)
    val testPersonalDetails: PersonalDetailsWithNino = PersonalDetailsWithNino("bob","smith",LocalDate.parse("1990-11-01 12:00:00.000", formatter),Nino("AA000002D"))
    val personalDetailsValidation: SuccessfulPersonalDetailsValidation = SuccessfulPersonalDetailsValidation(ValidationId(testValidationId),"success", testPersonalDetails, testLastUpdated)
    implicit val encryption: Encryption = app.injector.instanceOf[Encryption]
    val encryptedCredId: String = encryption.crypto.encrypt(PlainText(testCredId)).value
    val encryptedSessionID: String = encryption.crypto.encrypt(PlainText(headerCarrier.sessionId.get.value)).value

    val associationService = new AssociationService(associationRepository)
    val pdvService = new PersonalDetailsValidatorService(pdvRepository)


    val repoControlService = new RepoControlService(pdvService, associationService)
  }

}
