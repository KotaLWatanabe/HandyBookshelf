package com.handybookshelf
package util

import util.ISBN.*
import wvlet.airframe.ulid.ULID

import java.nio.charset.StandardCharsets

object ULIDConverter:
  def createULIDFromISBN(isbn: ISBN, timestamp: Timestamp): ULID = {
    // ISBNをバイト配列に変換し、10ビットに収まるよう調整
    val isbnBytes  = isbn.getBytes(StandardCharsets.UTF_8).take(10)
    val randomPart = isbnBytes.padTo(10, 0.toByte) // 足りない部分はゼロパディング

    // ULID生成 (6 bytes timestamp + 10 bytes random = 16 bytes total)
    ULID.fromBytes(timestamp.toBytes ++ randomPart)
  }

  def createULID(bookCode: String, timestamp: Timestamp): ULID =
    bookCode.isbnOpt match {
      case Some(isbn: ISBN) => createULIDFromISBN(isbn, timestamp)
      case None             =>
        // ISBNがない場合は完全ランダムな値
        val randomPart = Array.fill[Byte](10)(0.toByte)

        // ULID生成 (6 bytes timestamp + 10 bytes random = 16 bytes total)
        ULID.fromBytes(timestamp.toBytes ++ randomPart)
    }

  extension (timestamp: Timestamp) {
    def toBytes: Array[Byte] = {
      val millis = timestamp.epochMillis
      Array(
        (millis >> 40).toByte,
        (millis >> 32).toByte,
        (millis >> 24).toByte,
        (millis >> 16).toByte,
        (millis >> 8).toByte,
        millis.toByte
      )
    }
  }

  def extractISBNFromULID(ulid: ULID): Option[ISBN] = {
    val ulidBytes = ulid.toBytes
    // Skip the first 6 bytes (timestamp) and take the next 10 bytes (random/ISBN part)
    val randomPart = ulidBytes.slice(6, 16)
    if (randomPart.forall(_ == 0)) {
      None // ランダム部分がゼロの場合はISBNなしと判定
    } else {
      new String(randomPart.takeWhile(_ != 0), StandardCharsets.UTF_8).isbnOpt
    }
  }

  // 一致判定
  def isMatchingISBN(ulid: ULID, isbn: Option[String]): Boolean = {
    extractISBNFromULID(ulid) == isbn
  }
