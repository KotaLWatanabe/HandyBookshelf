package com.handybookshelf
package util

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type ISBNConstraint = DescribedAs[
  ForAll[Digit] & Xor[FixedLength[10], FixedLength[13]],
  "ISBN must be 10 or 13 digits."
]

opaque type ISBN = String :| ISBNConstraint
object ISBN extends RefinedType[String, ISBNConstraint]:
  extension (str: String) {
    def isbnOpt: Option[ISBN] = ISBN.option(str)
  }
  extension (isbn: ISBN) {
    def asNES: NES = isbn.nes
  }
