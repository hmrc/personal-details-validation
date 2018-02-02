package uk.gov.hmrc.audit

import javax.inject.{Inject, Singleton}

import play.api.Configuration

@Singleton
private class PlatformAnalyticsConnectorConfig @Inject()(configuration: Configuration) {

  lazy val baseUrl: String = ???
}