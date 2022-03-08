/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.Done
import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.datetime.CurrentTimeProvider
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.mongoEntity
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat._
import uk.gov.hmrc.personaldetailsvalidation.formats.TinyTypesFormats._
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.json.ops._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


private trait PersonalDetailsValidationRepository[Interpretation[_]] {

  def create(personalDetails: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): EitherT[Interpretation, Exception, Done]

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Interpretation[Option[PersonalDetailsValidation]]
}

@ImplementedBy(classOf[PersonalDetailsValidationMongoRepository])
private trait FuturedPersonalDetailsValidationRepository extends PersonalDetailsValidationRepository[Future]

@Singleton
private class PersonalDetailsValidationMongoRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig,
                                                                 mongoComponent: ReactiveMongoComponent)(implicit currentTimeProvider: CurrentTimeProvider)
  extends ReactiveRepository[PersonalDetailsValidation, ValidationId](
    collectionName = "personal-details-validation",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = mongoEntity(personalDetailsValidationFormats),
    idFormat = personalDetailsValidationIdFormats
  ) with FuturedPersonalDetailsValidationRepository with RetryMongoIndexes[PersonalDetailsValidation, ValidationId] {

  override val ttl: Long = config.collectionTtl.getSeconds

  def create(personalDetailsValidation: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): EitherT[Future, Exception, Done] = {

    val document = domainFormatImplicit.writes(personalDetailsValidation).as[JsObject].withCreatedTimeStamp(createdAtField)

    EitherT(collection.insert(ordered = false).one(document).map(_ => Right(Done))
      .recover {
        case ex: Exception => Left(ex)
      })
  }

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidation]] =
    findById(personalDetailsValidationId)

}
