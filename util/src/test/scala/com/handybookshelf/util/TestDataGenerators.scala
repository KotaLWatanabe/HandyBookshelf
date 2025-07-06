package com.handybookshelf
package util

import org.scalacheck.Gen
import wvlet.airframe.ulid.ULID
import com.handybookshelf.util.ISBN.*

trait TestDataGenerators:

  def nonEmptyString: Gen[NES] = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString.nes)

  def stringOfN(n: Int): Gen[NES] = Gen.listOfN(n, Gen.alphaNumChar).map(_.mkString.nes)

  def isbnDigits10: Gen[NES] = Gen.listOfN(10, Gen.numChar).map(_.mkString.nes)

  def isbnDigits13: Gen[NES] = Gen.listOfN(13, Gen.numChar).map(_.mkString.nes)

  def genValidISBN: Gen[ISBN] = Gen.oneOf(isbnDigits10, isbnDigits13).map(_.isbnOpt.get)

  def genTimestamp(): Gen[Timestamp] = Gen.choose(0L, System.currentTimeMillis()).map(Timestamp.fromEpochMillis)
  def genTimestampInRange(start: Timestamp = Timestamp.init, end: Timestamp): Gen[Timestamp] =
    Gen.choose(start.epochMillis, end.epochMillis).map(Timestamp.fromEpochMillis)

  def genULID(): Gen[ULID] = for {
    isbn      <- genValidISBN
    timestamp <- genTimestamp()
  } yield ULIDConverter.createULID(isbn.asNES, timestamp)

  def invalidISBN: Gen[NES] = Gen.oneOf(
    stringOfN(9),                                         // 9 digits
    stringOfN(11),                                        // 11 digits
    stringOfN(12),                                        // 12 digits
    stringOfN(14),                                        // 14 digits
    Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString.nes) // non-digits
  )

  def positiveInt: Gen[Int] = Gen.posNum[Int]

  def alphaString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  def alphaNumString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)

  def numericString: Gen[String] = Gen.nonEmptyListOf(Gen.numChar).map(_.mkString)
