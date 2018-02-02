package uk.gov.hmrc.random

import javax.inject.Singleton

import scala.util.Random

@Singleton
class RandomIntProvider extends (() => Int) {
  def apply(): Int = Random.nextInt()
}