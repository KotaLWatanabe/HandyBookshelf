package com.handybookshelf

import wvlet.airframe.ulid.ULID

import java.nio.charset.StandardCharsets
import ISBN.*

object ULIDConverter:
  def generateULIDFromISBN(isbn: ISBN, timestamp: Timestamp): ULID = {
    val timestampByte = timestamp.epochMillis.toByte

    // ISBNをバイト配列に変換し、80ビットに収まるよう調整
    val isbnBytes =
      isbn.getBytes(StandardCharsets.UTF_8).take(10) // 最大10バイト
    val randomPart = isbnBytes.padTo(10, 0.toByte) // 足りない部分はゼロパディング
    // ULID生成
    ULID.fromBytes(Array(timestampByte, randomPart*))
  }

  def generateULID(bookCode: String, timestamp: Timestamp): ULID =
    bookCode.isbnOpt match {
      case Some(isbn: ISBN) => generateULIDFromISBN(isbn, timestamp)
      case None             =>
        // ISBNがない場合は完全ランダムな値
        val randomPart = Array.fill[Byte](10)(0.toByte)

        val timestampByte = timestamp.epochMillis.toByte
        // ULID生成
        ULID.fromBytes(Array(timestampByte, randomPart*))
    }

  def extractISBNFromULID(ulid: ULID): Option[ISBN] = {
    val randomPart = ulid.toBytes.take(10)
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
//
//  def main(args: Array[String]): Unit = {
//    val isbnWithValue = Some("9781234567897")
//    val isbnWithoutValue = None
//
//    // ISBNありの場合のULID生成と復元
//    val ulidWithIsbn = generateULIDFromISBN(isbnWithValue)
//    println(s"Generated ULID (with ISBN): $ulidWithIsbn")
//    println(s"Extracted ISBN: ${extractISBNFromULID(ulidWithIsbn)}")
//
//    // ISBNなしの場合のULID生成と復元
//    val ulidWithoutIsbn = generateULIDFromISBN(isbnWithoutValue)
//    println(s"Generated ULID (without ISBN): $ulidWithoutIsbn")
//    println(s"Extracted ISBN: ${extractISBNFromULID(ulidWithoutIsbn)}")
//
//    // 一致判定テスト
//    println(s"Does the ULID match the original ISBN? ${isMatchingISBN(ulidWithIsbn, isbnWithValue)}")
//    println(s"Does the ULID match the original ISBN? ${isMatchingISBN(ulidWithoutIsbn, isbnWithoutValue)}")
//  }
