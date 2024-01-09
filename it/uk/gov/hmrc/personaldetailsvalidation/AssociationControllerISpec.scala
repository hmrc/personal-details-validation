package uk.gov.hmrc.personaldetailsvalidation

import play.api.http.MimeTypes
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.mvc.Http.HeaderNames.CONTENT_TYPE
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.model.{Association, PersonalDetailsWithNinoAndPostCode, SuccessfulPersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.personaldetailsvalidation.services.{AssociationService, Encryption, PersonalDetailsValidatorService}
import uk.gov.hmrc.support.BaseIntegrationSpec

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AssociationControllerISpec extends BaseIntegrationSpec with FutureAwaits with DefaultAwaitTimeout {

  val associationService: AssociationService = app.injector.instanceOf[AssociationService]
  val personalDetailsValidationService: PersonalDetailsValidatorService = app.injector.instanceOf[PersonalDetailsValidatorService]
  val encryption: Encryption = app.injector.instanceOf[Encryption]

  def retrieveBySession(body: String): Future[WSResponse] =
    wsUrl("/personal-details-validation/retrieve-by-session")
      .addHttpHeaders(CONTENT_TYPE -> MimeTypes.JSON)
      .post(body)

  "/retrieve-by-session" should {
    s"return $OK" when {
      "association record exists & PDV record exists and PDV record is success" in {
        val credId = "cred1Foo"
        val sessionId = "sessionId1Foo"
        val dateTimeNow = LocalDateTime.of(2020,1,1,1,1)
        val validationId = ValidationId(UUID.fromString("928b39f3-98f7-4a0b-bcfe-9065c1175d1e"))
        val association = Association(
          encryption.crypto.encrypt(PlainText(credId)).value,
          encryption.crypto.encrypt(PlainText(sessionId)).value,
          validationId.value.toString,
          dateTimeNow
        )

        val successPDVRecord = SuccessfulPersonalDetailsValidation(
          id = validationId,
          personalDetails = PersonalDetailsWithNinoAndPostCode(
            "first","last", LocalDate.of(2022,12,2), Nino("AA111111A"), "posty"),
          createdAt = dateTimeNow
        )
        await(associationService.insertRecord(association))
        await(personalDetailsValidationService.insertRecord(successPDVRecord).value)

        val result = await(retrieveBySession(Json.obj("credentialId" -> credId, "sessionId" -> sessionId).toString()))

        result.status mustBe OK
        result.json mustBe Json.obj(
          "id" -> "928b39f3-98f7-4a0b-bcfe-9065c1175d1e",
          "validationStatus" -> "success",
          "personalDetails" -> Json.obj(
            "firstName" -> "first",
            "lastName" -> "last",
            "dateOfBirth" -> "2022-12-02",
            "postCode" -> "posty",
            "nino" -> "AA111111A"
          ),
          "createdAt" -> Json.obj(
            "$date" -> Json.obj(
              "$numberLong" -> "1577840460000"
            )
          ),
          "deceased" -> false
        )

      }
      "association record exists & PDV record exists and PDV record is fail" in {

      }
    }
    s"return $NOT_FOUND" when {
      "association record does not exist" in {

      }
      "pdv record does not exist" in {

      }
    }
    s"return $BAD_REQUEST" when {
      "missing parameters" in {

      }
      "parameters are blank" in {

      }
    }
  }
}
