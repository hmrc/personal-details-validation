package uk.gov.hmrc.support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status._

object AuthenticatorStub {

  private val path: String = "/authenticator/match"

  def expecting(personalDetailsJson: String) = new {

    private val mappingBuilder = post(urlEqualTo(path)).withRequestBody(equalToJson(personalDetailsJson, true, false))

    def respondWithOK(): Unit = {
      stubFor(
        mappingBuilder.willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """{
                  | "firstName":"Jim",
                  | "lastName":"Ferguson",
                  | "dateOfBirth":"1948-04-23",
                  | "nino":"AA000003D",
                  | "saUtr":"1097133333"
                  |}
                  | """.stripMargin)
          )
      )
    }

    def respondWith(status: Int): Unit = {
      stubFor(
        mappingBuilder.willReturn(
          aResponse().withStatus(status)
        )
      )
    }
  }


}
