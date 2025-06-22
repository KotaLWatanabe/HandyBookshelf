package com.handybookshelf
package domain

import util.IDGenerator._idgen
import util.IDGenerator
import org.atnos.eff.Eff
import wvlet.airframe.ulid.ULID

final case class UserAccountId private (private val value: ULID) extends AnyVal {
  def breachEncapsulationIdAsString: String = value.toString
}
object UserAccountId:
  def generate[R: _idgen](): Eff[R, UserAccountId] =
    IDGenerator.generateTo[UserAccountId, R](UserAccountId(_))

  def create(value: ULID): UserAccountId = UserAccountId(value)
