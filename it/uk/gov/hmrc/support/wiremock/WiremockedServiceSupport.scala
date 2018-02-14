package uk.gov.hmrc.support.wiremock

import uk.gov.hmrc.support.wiremock.WiremockConfiguration._

trait WiremockedServiceSupport {

  val wiremockedServices = List[String]()

  lazy val wiremockedServicesConfiguration: Map[String, Any] = {
    val servicesConfig = wiremockedServices.foldLeft(Map.empty[String, Any]) { (result, service) =>
      result ++ Map(
        s"microservice.services.$service.host" -> wiremockHost,
        s"microservice.services.$service.port" -> wiremockPort
      )
    }

    val auditingConfig = Map[String, Any](
      "auditing.consumer.baseUri.host" -> wiremockHost,
      "auditing.consumer.baseUri.port" -> wiremockPort
    )

    servicesConfig ++ auditingConfig
  }
}
