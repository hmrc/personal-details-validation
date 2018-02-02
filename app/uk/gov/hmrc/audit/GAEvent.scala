package uk.gov.hmrc.audit

trait GAEvent {
  def category: String
  def action: String
  def label: String
}
