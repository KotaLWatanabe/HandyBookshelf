package com.handybookshelf
package util

import cats.Eval
import org.atnos.eff.*
import org.atnos.eff.all._eval
import wvlet.airframe.ulid.ULID

object ULIDGenerator:
  private[util] def apply: Eval[ULID] = Eval.later(ULID.newULID)

  def generate[R: _eval]: Eff[R, ULID] = Eff.send[Eval[*], R, ULID](ULIDGenerator.apply)

  def generateSafeTo[A, R: _eval](f: ULID => A): Eff[R, A] = generate.map(f)
