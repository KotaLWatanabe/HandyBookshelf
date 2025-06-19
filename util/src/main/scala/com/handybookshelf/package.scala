package com

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

package object handybookshelf:
  opaque type NES = String :|
    DescribedAs[MinLength[
      1
    ], """NES (Non-empty String) must have at least 1characters."""]

  extension (str: String) {
    def nes: NES = str.refineUnsafe
  }

