package uk.gov.hmrc.support.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.{JsNull, JsObject, Json}

object AuthStub {

  val credId: String = "cred-123"

  def authBody(credId: String): JsObject = Json.obj(
    "optionalCredentials" -> Json.obj(
      "providerId" -> credId,
      "providerType" -> "test2"
    )
  )

  val authBodyNoCredID: JsObject = Json.obj(
    "optionalCredentials" -> Json.obj(
      "providerId" -> JsNull,
      "providerType" -> "test2"
    )
  )


  def stubForAuth(statusCode: Int, body: JsObject = authBody(credId)): StubMapping = {
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(Json.stringify(body))
            .withHeader("Content-type", "Application/json")
        )
    )
  }
}


