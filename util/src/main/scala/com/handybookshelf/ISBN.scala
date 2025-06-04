package com.handybookshelf

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type ISBN = String :|
  DescribedAs[
    ForAll[Digit] & Xor[FixedLength[10], FixedLength[13]],
    "ISBN must be 10 or 13 digits."
  ]

object ISBN:
  extension (str: String) {
    def isbnOpt: Option[ISBN] = str.refineOption
  }
