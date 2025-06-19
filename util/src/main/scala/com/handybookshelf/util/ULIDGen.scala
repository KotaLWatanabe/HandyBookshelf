package com.handybookshelf
package util

import cats.effect.IO
import wvlet.airframe.ulid.ULID

object ULIDGen:
  def generate: IO[ULID] = IO(ULID.newULID)
