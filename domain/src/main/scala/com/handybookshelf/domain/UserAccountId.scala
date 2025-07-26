package com.handybookshelf
package domain

import util.ULIDGenerator
import org.atnos.eff.Eff
import org.atnos.eff.all._eval
import wvlet.airframe.ulid.ULID

final case class UserAccountId private (private val value: ULID) extends AnyVal {
  def breachEncapsulationIdAsString: String = value.toString
}
object UserAccountId:
  def generate[R: _eval]: Eff[R, UserAccountId] =
    ULIDGenerator.generateSafeTo[UserAccountId, R](UserAccountId(_))

  def create(value: ULID): UserAccountId = UserAccountId(value)
