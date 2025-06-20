package com.handybookshelf
package domain

import cats.effect.IO
import com.handybookshelf.util.ULIDGen
import wvlet.airframe.ulid.ULID

final case class UserAccountId private (private val value: ULID) extends AnyVal {
  def breachEncapsulationAsString: String = value.toString
}
object UserAccountId {
  def create(): IO[UserAccountId] = ULIDGen.generate.map(UserAccountId.apply)
}