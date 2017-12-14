package uk.gov.hmrc.personaldetailsvalidation

import java.time.Duration
import javax.inject.{Inject, Singleton}

import play.api.Configuration
import uk.gov.hmrc.config.BaseConfig

@Singleton
private class PersonalDetailsValidationMongoRepositoryConfig @Inject()(protected val configuration: Configuration) extends BaseConfig {

  private implicit val isoDateTimeStringToDuration: String => Option[Duration] = str => {
    val durationValue = configuration.loadMandatory[String](str)
    Some(Duration.parse(durationValue))
  }

  lazy val collectionTtl: Duration = Duration.from(configuration.loadMandatory[Duration]("mongodb.collections.personal-details-validation.ttl"))

}
