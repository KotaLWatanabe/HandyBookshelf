package com.handybookshelf
package util

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type ISBN = String :|
  DescribedAs[
    ForAll[Digit] & Xor[FixedLength[10], FixedLength[13]],
    "ISBN must be 10 or 13 digits."
  ]

object ISBN:
  extension (str: NES) {
    def isbnOpt: Option[ISBN] = str.refineOption
  }
  extension (isbn: ISBN) {
    def asNES: NES = isbn.nes
  }
