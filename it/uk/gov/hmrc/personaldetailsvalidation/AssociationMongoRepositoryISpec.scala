/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.personaldetailsvalidation

import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.IndexModel
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.personaldetailsvalidation.model.Association

import scala.concurrent.ExecutionContext
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

class AssociationMongoRepositoryISpec extends AnyWordSpec
  with Matchers
  with GuiceOneServerPerSuite
  with BeforeAndAfterEach
  with DefaultPlayMongoRepositorySupport[Association] {

  val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val dateToMillis: LocalDateTime => Long = {localDateTime => localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli}

  val config: PersonalDetailsValidationMongoRepositoryConfig = app.injector.instanceOf[PersonalDetailsValidationMongoRepositoryConfig]

  override lazy val repository = new AssociationMongoRepository(config, mongoComponent)(ec)

  override def beforeEach(): Unit = {
    super.beforeEach()
    deleteAll().futureValue
  }

  "AssociationMongoRepository" should {

    "be able to save an instance of Association and then retrieve the saved data using the credential and session id" in new Setup {

      val lastUpdated: LocalDateTime = LocalDateTime.now()

      val association: Association = Association(testCredId1, testSessionId1, testValidationId1, lastUpdated)

      repository.insertRecord(association).futureValue

      repository.getRecord(testCredId1, testSessionId1).futureValue match {
        case Some(retrieved) =>
          retrieved.credentialId shouldBe testCredId1
          retrieved.sessionId shouldBe testSessionId1
          retrieved.validationId shouldBe testValidationId1
          retrieved.lastUpdated shouldBe Instant.ofEpochMilli(dateToMillis(lastUpdated)).atZone(ZoneOffset.UTC).toLocalDateTime
        case None => fail("Expected instance of association was not retrieved")
      }

    }

    "be able to save two different instances of association" in new Setup {

      val association1: Association = Association(testCredId1, testSessionId1, testValidationId1, testLastUpdated1)
      val association2: Association = Association(testCredId2, testSessionId2, testValidationId2, testLastUpdated2)

      repository.insertRecord(association1).futureValue
      repository.insertRecord(association2).futureValue

      count().futureValue shouldBe 2L

      repository.getRecord(testCredId1, testSessionId1).futureValue match {
        case Some(association) => association shouldBe association1
        case None => fail("Expected instance of association1 was not retrieved")
      }

      repository.getRecord(testCredId2, testSessionId2).futureValue match {
        case Some(association) => association shouldBe association2
        case None => fail("Expected instance of association2 was not retrieved")
      }
    }

    "return none when a matching association cannot be found" in new Setup {

      repository.getRecord(testCredId1, testSessionId1).futureValue match {
        case Some(association) => fail("No association should be returned")
        case None => succeed
      }

    }

    "update existing records with the same compound index" in new Setup {

      val association1: Association = Association(testCredId1, testSessionId1, testValidationId1, testLastUpdated1)

      repository.insertRecord(association1).futureValue

      repository.getRecord(testCredId1, testSessionId1).futureValue match {
        case Some(association) => association.lastUpdated shouldBe testLastUpdated1
        case None => fail("Expected instance of association1 was not retrieved")
      }

      val association2: Association = Association(testCredId1, testSessionId1, testValidationId1, testLastUpdated2)

      repository.insertRecord(association2).futureValue

      count().futureValue shouldBe 1L

      repository.getRecord(testCredId1, testSessionId1).futureValue match {
        case Some(association) => association.lastUpdated shouldBe testLastUpdated2
        case None => fail("Expected instance of association2 was not retrieved")
      }
    }

    "insert two instances of association with differing credential identifiers" in new Setup {

      val association1: Association = Association(testCredId1, testSessionId1, testValidationId1, testLastUpdated1)
      val association2: Association = Association(testCredId2, testSessionId1, testValidationId1, testLastUpdated1)

      repository.insertRecord(association1).futureValue
      repository.insertRecord(association2).futureValue

      count().futureValue shouldBe 2L

      repository.getRecord(testCredId1, testSessionId1).futureValue match {
        case Some(association) => association shouldBe association1
        case None => fail("Expected instance of association1 was not retrieved")
      }

      repository.getRecord(testCredId2, testSessionId1).futureValue match {
        case Some(association) => association shouldBe association2
        case None => fail("Expected instance of association2 was not retrieved")
      }

    }

    "insert two instances of association with differing session identifiers" in new Setup {

      val association1: Association = Association(testCredId1, testSessionId1, testValidationId1, testLastUpdated1)
      val association2: Association = Association(testCredId1, testSessionId2, testValidationId1, testLastUpdated1)

      repository.insertRecord(association1).futureValue
      repository.insertRecord(association2).futureValue

      count().futureValue shouldBe 2L

      repository.getRecord(testCredId1, testSessionId1).futureValue match {
        case Some(association) => association shouldBe association1
        case None => fail("Expected instance of association1 was not retrieved")
      }

      repository.getRecord(testCredId1, testSessionId2).futureValue match {
        case Some(association) => association shouldBe association2
        case None => fail("Expected instance of association2 was not retrieved")
      }

    }

    "set a time to live duration for the association documents of one day" in new Setup {

      val indexes: Seq[IndexModel] = repository.indexes

      indexes.size shouldBe 2

      val expireAfterSecondsIndexes: Seq[IndexModel] = indexes.filter(_.getOptions.getName == "expireAfterSeconds")

      if(expireAfterSecondsIndexes.size == 1){
        expireAfterSecondsIndexes.head.getKeys.toBsonDocument shouldBe BsonDocument("lastUpdated" -> 1)
        expireAfterSecondsIndexes.head.getOptions.getExpireAfter(TimeUnit.SECONDS) shouldBe secondsInDay
      } else {
        fail("An error occurred attempting to determine the Time to live index \"expireAfterSeconds\"")
      }

    }

  }

  trait Setup {

    val dateFormat: String = "yyyy-MM-dd HH:mm:ss.SSS"
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat)

    val secondsInDay: Long = 24 * 60 * 60

    val testCredId1: String = "cred-123"
    val testSessionId1: String = s"session-${UUID.randomUUID().toString}"
    val testValidationId1: String = UUID.randomUUID().toString
    val testLastUpdated1: LocalDateTime = LocalDateTime.parse("2023-11-01 12:00:00.000", formatter)

    val testCredId2: String = "cred-456"
    val testSessionId2: String = s"session-${UUID.randomUUID().toString}"
    val testValidationId2: String = UUID.randomUUID().toString
    val testLastUpdated2: LocalDateTime = LocalDateTime.parse("2023-11-01 12:30:00.000", formatter)

  }

}
