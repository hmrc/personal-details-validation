/*
 * Copyright 2018 HM Revenue & Customs
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

package generators

import org.scalacheck.Gen
import org.scalacheck.Gen.{listOfN, numChar, oneOf}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.model.{ValidationId, ValidationStatus}

trait ValueGenerators extends Generators {

  implicit val ninos: Gen[Nino] = for {
    validPrefix <- Gen.oneOf(Nino.validPrefixes)
    middleSixDigits <- listOfN(6, numChar).map(_.mkString)
    validSuffix <- Gen.oneOf(Nino.validSuffixes)
  } yield Nino(s"$validPrefix$middleSixDigits$validSuffix")

  implicit val validationStatuses: Gen[ValidationStatus] = oneOf(ValidationStatus.all)

  implicit val validationIds: Gen[ValidationId] = Gen.uuid.map(ValidationId(_))

}
