package uk.gov.hmrc.support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import play.api.http.Status.OK
import play.api.libs.json.JsValue

object CitizenDetailsStub extends Eventually with IntegrationPatience {

  private val path: String = "/citizen-details/AA000003D/designatory-details"

  def expecting() = new {

    private val mappingBuilder = get(urlEqualTo(path))

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

    def respondWith(status: Int, body: Option[JsValue] = None): Unit = {
      stubFor(
        mappingBuilder.willReturn(
          aResponse().withStatus(status).withBody(body.map(_.toString()).getOrElse(""))
        )
      )
    }
  }
}
