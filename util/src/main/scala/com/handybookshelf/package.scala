package com

import io.circe.{Decoder, Encoder}
import scala.compiletime.{error, requireConst}

package object handybookshelf:
  opaque type NonEmptyString <: String = String
  object NonEmptyString:
    def apply(s: String): Option[NonEmptyString] =
      if s.isEmpty then None else Some(s)

    inline def from(inline s: String): NonEmptyString =
      requireConst(s)
      inline if s == "" then error("got an empty string") else s

    def unsafeNonEmptyString(s: String): NonEmptyString = apply(s).getOrElse(
      throw new IllegalArgumentException("NonEmptyString must not be empty")
    )

  given Conversion[NonEmptyString, String]:
    inline def apply(nes: NonEmptyString): String = nes

  type NES = NonEmptyString

  extension (str: String) {
    def nes: NES            = NonEmptyString.unsafeNonEmptyString(str)
    def nesOpt: Option[NES] = NonEmptyString(str)
  }

  // Circe codecs for NES
  given Encoder[NES] = Encoder.encodeString.contramap(identity)
  given Decoder[NES] = Decoder.decodeString.map(_.nes)
