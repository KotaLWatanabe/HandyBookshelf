package com.handybookshelf
package domain

import wvlet.airframe.ulid.ULID

final case class UserAccountId private (private val value: ULID) extends AnyVal {
//  def breachEncapsulationAsString: String = value.toString
}
object UserAccountId {
  def create(): UserAccountId = new UserAccountId(ULID.newULID)
}