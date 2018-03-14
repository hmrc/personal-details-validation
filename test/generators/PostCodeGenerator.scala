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

  private val invalidCharacters : List[Char] = List('C', 'I', 'K', 'M', 'O', 'V', 'c', 'i', 'k', 'm', 'o', 'v')
  private val postCodeArea : Gen[String] = oneOf(postCodeAreas)
  private val unitDigitsAllowedUpper = "ABDEFGHJLNPQRSTUWXYZ".toCharArray
  private val unitDigitsAllowedLower = unitDigitsAllowedUpper.map(_.toLower)
  private val unitDigit = Gen.frequency((1, oneOf(unitDigitsAllowedLower)), (5, oneOf(unitDigitsAllowedUpper)))

  private def outbound(area: String): Gen[String] = {
    area match {
      case _ if outboundOptions.keySet.contains(area) => oneOf(outboundOptions(area))
      case _ => Gen.choose(1, 99).map(_.toString)
    }
  }

  private def capitalizeAreaCharacter(area: String, position: Int, randomNumber: Int) : String = {
    area.length match {
      case size if size < position => ""
      case _ if (randomNumber % 3 == 0) => area(position - 1).toLower.toString
      case _ => area(position - 1).toString
    }
  }

  implicit val postCode  : Gen[String] = for {
    area <- postCodeArea
    district <- outbound(area)
    area1Randomized <- Gen.choose(1, 1000).map(capitalizeAreaCharacter(area, 1, _))
    area2Randomized <- Gen.choose(1, 1000).map(capitalizeAreaCharacter(area, 2, _))
    space <- Gen.choose(1, 1000).map(param => if(param % 5 == 0) "" else " ")
    sector <- Gen.choose(1, 9)
    unit <- Gen.listOfN(2, unitDigit)
  } yield area1Randomized + area2Randomized + district + space + sector + unit.mkString
}
