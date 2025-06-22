package com.handybookshelf
package util

import org.atnos.eff.*
import wvlet.airframe.ulid.ULID

type ULIDGen[A] = A => ULID
object ULIDGen:
  def apply[A]: ULIDGen[A] = _ => ULID.newULID

object IDGenerator:
  type S1IDGen   = Fx.fx1[ULIDGen]
  type _idgen[R] = ULIDGen |= R

  def generate[R: _idgen]: Eff[R, ULID] = Eff.send[ULIDGen[*], R, ULID](ULIDGen.apply)

  def generateTo[A, R: _idgen](f: ULID => A): Eff[R, A] = generate.map(f)
