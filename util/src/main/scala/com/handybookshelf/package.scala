package com

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.circe.{Decoder, Encoder}

package object handybookshelf:
  opaque type NES = String :|
    DescribedAs[MinLength[
      1
    ], """NES (Non-empty String) must have at least 1characters."""]

  extension (str: String) {
    def nes: NES            = str.refineUnsafe
    def nesOpt: Option[NES] = str.refineOption
  }

  extension (nes: NES) {
    def size: Int        = nes.length
    def isEmpty: Boolean = nes.isEmpty
  }

  // Circe codecs for NES
  given Encoder[NES] = Encoder.encodeString.contramap(_.toString)
  given Decoder[NES] = Decoder.decodeString.map(_.nes)
