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

package generators

import org.scalacheck.Gen
import org.scalacheck.Gen.oneOf

object PostCodeGenerator extends Generators {
  // List of postcode areas obtained from https://en.wikipedia.org/wiki/List_of_postcode_areas_in_the_United_Kingdom
  private val postCodeAreas = List("AB", "AL", "B", "BA", "BB", "BD", "BH", "BL", "BN", "BR", "BS", "BT", "CA",
    "CB", "CF", "CH", "CM", "CO", "CR", "CT", "CV", "CW", "DA", "DD", "DE", "DG", "DH", "DL", "DN", "DT", "DY",
    "E", "EC", "EH", "EN", "EX", "FK", "FY", "G", "GL", "GU", "HA", "HD", "HG", "HP", "HR", "HS", "HU", "HX",
    "IG", "IP", "IV", "KA", "KT", "KW", "KY", "L", "LA", "LD", "LE", "LL", "LN", "LS", "LU", "M", "ME", "MK",
    "ML", "N", "NE", "NG", "NN", "NP", "NR", "NW", "OL", "OX", "PA", "PE", "PH", "PL", "PO", "PR", "RG", "RH",
    "RM", "S", "SA", "SE", "SG", "SK", "SL", "SM", "SN", "SO", "SP", "SR", "SS", "ST", "SW", "SY", "TA", "TD",
    "TF", "TN", "TQ", "TR", "TS", "TW", "UB", "W", "WA", "WC", "WD", "WF", "WN", "WR", "WS", "WV", "YO", "ZE")

  private val outboundOptions: Map[String, List[String]] = Map(
    "WC" -> List("1A", "1B", "1E", "1H", "1N", "1R", "1V", "1X", "2A", "2B", "2E", "2H", "2N", "2R"),
    "EC" -> List("1A", "1M", "1N", "1P", "1R", "1V", "1Y", "2A", "2M", "2N", "2P", "2R", "2V", "2Y", "3A", "3M", "3N",
      "3P", "3R", "3V", "4A", "4M", "4N", "4P", "4R", "4V", "4Y")
  )

  private val unitDigits = oneOf("ABDEFGHJLNPQRSTUWXYZ".toCharArray)

  private def outbound(area: String): Gen[String] = {
    area match {
      case _ if outboundOptions.keySet.contains(area) => oneOf(outboundOptions(area))
      case _ => Gen.choose(1, 99).map(_.toString)
    }
  }

  private def randomiseCase(part: String): String = {
    part.toList.map{ character =>
      if (Gen.choose(1, 1000).sample.get % 3 == 0)
        character.toLower
      else
        character
    }.mkString
  }

  implicit val postCode  : Gen[String] = for {
    area <- oneOf(postCodeAreas)
    district <- outbound(area)
    space <- Gen.choose(1, 1000).map(param => if(param % 5 == 0) "" else " ")
    sector <- Gen.choose(1, 9)
    unit <- Gen.listOfN(2, unitDigits)
  } yield randomiseCase(area + district + space + sector + unit.mkString)
}
