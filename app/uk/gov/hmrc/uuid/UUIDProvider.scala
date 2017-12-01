package uk.gov.hmrc.uuid

import java.util.UUID

import com.google.inject.Singleton

@Singleton
class UUIDProvider extends (() => UUID) {
  def apply(): UUID = UUID.randomUUID()
}