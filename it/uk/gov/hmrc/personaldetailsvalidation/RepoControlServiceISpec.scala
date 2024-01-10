package uk.gov.hmrc.personaldetailsvalidation

import akka.Done
import cats.data.EitherT
import ch.qos.logback.classic.Level
import org.scalatest.{BeforeAndAfterEach, LoneElement}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsWithNino, SuccessfulPersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.personaldetailsvalidation.services.{AssociationService, Encryption, PersonalDetailsValidatorService, RepoControlService}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class RepoControlServiceISpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterEach
  with GuiceOneServerPerSuite
  with ScalaFutures
  with Eventually
  with LogCapturing
  with LoneElement {

  "RepoControlService" should {
    "save an instant of Association and personal details" in new Setup {

      val timeBeforeTest: LocalDateTime = LocalDateTime.now

      val result: EitherT[Future, Exception, Done] = eventually(repoControlService.insertPDVAndAssociationRecord(personalDetailsValidation,Some(testCredId))(headerCarrier, ec, encryption))

      eventually(result.value.futureValue)

      val timeAfterTest: LocalDateTime = LocalDateTime.now

      eventually(associationService.getRecord(testCredId, headerCarrier.sessionId.get.value).futureValue match {
        case Some(retrieved) =>
          retrieved.credentialId shouldBe encryptedCredId
          retrieved.sessionId shouldBe encryptedSessionID
          retrieved.validationId shouldBe testValidationId.toString
          retrieved.lastUpdated.isAfter(timeBeforeTest) || retrieved.lastUpdated.isEqual(timeBeforeTest) shouldBe true
          retrieved.lastUpdated.isBefore(timeAfterTest) || retrieved.lastUpdated.isEqual(timeAfterTest) shouldBe true

        case None =>
          fail("Expected instance of association was not retrieved")
        }
      )

      eventually(pdvService.getRecord(ValidationId(testValidationId))(ec).futureValue match {
        case Some(retrieved) =>
          retrieved.id.value.toString shouldBe testValidationId.toString
        case None => fail("Expected instance of personalDetails was not retrieved")
        }
      )
    }

    "if the Association already exists it still succeeds and doesn't effect the user" in new Setup {
      val timeBeforeFirstCall: LocalDateTime = LocalDateTime.now
      val resultOfFirstCall: EitherT[Future, Exception, Done] = eventually(repoControlService.insertPDVAndAssociationRecord(personalDetailsValidation, Some(testCredId))(headerCarrier, ec, encryption))
      resultOfFirstCall.value.futureValue
      val resultOfSecondCall: EitherT[Future, Exception, Done] = eventually(repoControlService.insertPDVAndAssociationRecord(personalDetailsValidation, Some(testCredId))(headerCarrier, ec, encryption))
      resultOfSecondCall.value.futureValue
      val timeAfterSecondCall: LocalDateTime = LocalDateTime.now

      eventually(associationService.getRecord(testCredId, headerCarrier.sessionId.get.value).futureValue match {
        case Some(retrieved) =>
          retrieved.credentialId shouldBe encryptedCredId
          retrieved.sessionId shouldBe encryptedSessionID
          retrieved.validationId shouldBe testValidationId.toString
          retrieved.lastUpdated.isAfter(timeBeforeFirstCall) || retrieved.lastUpdated.isEqual(timeBeforeFirstCall) shouldBe true
          retrieved.lastUpdated.isBefore(timeAfterSecondCall) || retrieved.lastUpdated.isEqual(timeAfterSecondCall) shouldBe true
        case None =>
          fail("Expected instance of association was not retrieved")
        }
      )
      eventually(pdvService.getRecord(ValidationId(testValidationId))(ec).futureValue match {
        case Some(retrieved) =>
          retrieved.id.value.toString shouldBe testValidationId.toString
        case None => fail("Expected instance of personalDetails was not retrieved")
        }
      )
    }

    "not add an association repo entry if details are missing but still succeeds and doesn't effect the user" in new Setup {
      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>

        val resultOfFirstCall: EitherT[Future, Exception, Done] = eventually(repoControlService.insertPDVAndAssociationRecord(personalDetailsValidation, None)(headerCarrier, ec, encryption))
        resultOfFirstCall.value.futureValue
        eventually(associationService.getRecord(encryptedCredId, encryptedSessionID).futureValue shouldBe None)
        eventually(pdvService.getRecord(ValidationId(testValidationId))(ec)).futureValue match {
          case Some(retrieved) =>
            retrieved.id.value.toString shouldBe testValidationId.toString
          case None => fail("Expected instance of personalDetails was not retrieved")
        }
        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage shouldBe "adding to Association database rejected due to credID does not exist"
        }
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
    val config: PersonalDetailsValidationMongoRepositoryConfig = app.injector.instanceOf[PersonalDetailsValidationMongoRepositoryConfig]
    val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    lazy val associationRepository: AssociationMongoRepository = app.injector.instanceOf[AssociationMongoRepository]
    lazy val pdvRepository: PdvRepository = app.injector.instanceOf[PdvRepository]
    val associationService: AssociationService = app.injector.instanceOf[AssociationService]
    val pdvService: PersonalDetailsValidatorService = app.injector.instanceOf[PersonalDetailsValidatorService]
    val repoControlService: RepoControlService = app.injector.instanceOf[RepoControlService]
  }

}
