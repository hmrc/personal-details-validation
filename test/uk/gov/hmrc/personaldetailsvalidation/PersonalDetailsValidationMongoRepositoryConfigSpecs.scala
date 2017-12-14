package uk.gov.hmrc.personaldetailsvalidation

import java.time.Duration
import java.time.format.DateTimeParseException

import play.api.Configuration
import setups.ConfigSetup
import uk.gov.hmrc.play.test.UnitSpec

class PersonalDetailsValidationMongoRepositoryConfigSpecs extends UnitSpec {

  "config" should {
    "return ttl duration from configuration" in new Setup {
      whenConfigEntriesExists("mongodb.collections.personal-details-validation.ttl" -> "P1D"){ config =>
        config.collectionTtl shouldBe Duration.ofDays(1)
      }
    }

    "throw DateTimeParseException if configured ttl is not a valid ISO date time syntax" in new Setup {
      whenConfigEntriesExists("mongodb.collections.personal-details-validation.ttl" -> "P1 D"){ config =>
        a[DateTimeParseException] should be thrownBy config.collectionTtl
      }
    }

    "throw RuntimeException if ttl is not configured" in new Setup {
      whenConfigEntriesExists(){ config =>
        a[RuntimeException] should be thrownBy config.collectionTtl
      }
    }
  }

  private trait Setup extends ConfigSetup[PersonalDetailsValidationMongoRepositoryConfig] {
    val newConfigObject: Configuration => PersonalDetailsValidationMongoRepositoryConfig = new PersonalDetailsValidationMongoRepositoryConfig(_)
  }


}
