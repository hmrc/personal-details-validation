/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.personaldetailsvalidation.services

import uk.gov.hmrc.domain.Nino

import javax.inject.{Inject, Singleton}

@Singleton
class NinoKeyService @Inject()() {
  // Normalisation equivalent to Authenticator: strip whitespace, uppercase, drop the suffix letter.
  // NEVER log the raw NINO or this key.
  def ninoKey(nino: Nino): String =
    nino.value.replaceAll("\\s", "").toUpperCase.take(8)
}
