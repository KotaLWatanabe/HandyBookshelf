package com.handybookshelf
package domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class BookData(
    isbn: Option[String] = None,
    title: String,
    author: Option[String] = None,
    publisher: Option[String] = None,
    publishedYear: Option[Int] = None
)

object BookData:
  given Encoder[BookData] = deriveEncoder
  given Decoder[BookData] = deriveDecoder