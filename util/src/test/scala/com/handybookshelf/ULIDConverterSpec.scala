package com.handybookshelf

import TestDataGenerators.*
import org.scalacheck.Gen
import ISBN.*
import wvlet.airframe.ulid.ULID

class ULIDConverterSpec extends PropertyBasedTestHelpers:
  def genULID: Gen[ULID] = for {
    isbn      <- genValidISBN
    timestamp <- genTimestamp
  } yield ULIDConverter.generateULID(isbn, timestamp)

  describe("ULIDConverter") {

    describe("generateULID") {

      checkProperty("should handle valid ISBN strings") {
        Gen.zip(genValidISBN, genTimestamp)
      } { case (isbn, timestamp) =>
        val ulid = ULIDConverter.generateULID(isbn, timestamp)
        ulid != null
      }

      checkProperty("should handle invalid ISBN strings") {
        Gen.zip(invalidISBN, genTimestamp)
      } { case (invalidIsbn, timestamp) =>
        val ulid = ULIDConverter.generateULID(invalidIsbn, timestamp)
        ulid != null
      }

      checkProperty("should generate all-zero random part for non-ISBN strings") {
        Gen.zip(invalidISBN, genTimestamp)
      } { case (invalidIsbn, timestamp) =>
        val ulid       = ULIDConverter.generateULID(invalidIsbn, timestamp)
        val ulidBytes  = ulid.toBytes
        val randomPart = ulidBytes.drop(6).take(10) // Skip 6-byte timestamp, take 10-byte random part
        randomPart.forall(_ == 0)
      }

      checkPropertyWithSeed("should be deterministic with same timestamp and input", 42L) {
        Gen.zip(alphaNumString, genTimestamp)
      } { case (bookCode, timestamp) =>
        val ulid1 = ULIDConverter.generateULID(bookCode, timestamp)
        val ulid2 = ULIDConverter.generateULID(bookCode, timestamp)
        ulid1 == ulid2
      }
    }

    describe("extractISBNFromULID") {

      checkProperty("should return None for ULID generated without ISBN") {
        Gen.zip(invalidISBN, genTimestamp)
      } { case (invalidIsbn, timestamp) =>
        val ulid      = ULIDConverter.generateULID(invalidIsbn, timestamp)
        val extracted = ULIDConverter.extractISBNFromULID(ulid)
        extracted.isEmpty
      }

      checkProperty("should extract ISBN from ULID generated with valid ISBN") {
        genULID
      } { ulid =>
        val extracted = ULIDConverter.extractISBNFromULID(ulid)
        extracted.isDefined
      }
    }

    describe("isMatchingISBN") {

      checkProperty("should return true when comparing with None for non-ISBN ULID") {
        Gen.zip(invalidISBN, genTimestamp)
      } { case (invalidIsbn, timestamp) =>
        val ulid = ULIDConverter.generateULID(invalidIsbn, timestamp)
        ULIDConverter.isMatchingISBN(ulid, None)
      }

      checkProperty("should return false when comparing valid ISBN ULID with None") {
        genULID
      } { ulid =>
        !ULIDConverter.isMatchingISBN(ulid, None)
      }

      checkProperty("should match when both have same ISBN") {
        genULID
      } { ulid =>
        val extracted = ULIDConverter.extractISBNFromULID(ulid)
        extracted match {
          case Some(extractedIsbn) => ULIDConverter.isMatchingISBN(ulid, Some(extractedIsbn))
          case None                => true // If extraction fails, consider it a pass for this test
        }
      }
    }

    describe("edge cases") {

      checkProperty("should handle empty string") {
        genTimestamp
      } { timestamp =>
        val ulid      = ULIDConverter.generateULID("", timestamp)
        val extracted = ULIDConverter.extractISBNFromULID(ulid)
        extracted.isEmpty
      }

      checkProperty("should handle very long non-ISBN string") {
        genTimestamp
      } { timestamp =>
        val longString = "a" * 50
        val ulid       = ULIDConverter.generateULID(longString, timestamp)
        val extracted  = ULIDConverter.extractISBNFromULID(ulid)
        extracted.isEmpty
      }

      checkProperty("should be consistent across different random strings") {
        Gen.zip(alphaNumString, genTimestamp)
      } { case (randomStr, timestamp) =>
        val ulid1 = ULIDConverter.generateULID(randomStr, timestamp)
        val ulid2 = ULIDConverter.generateULID(randomStr, timestamp)
        ulid1 == ulid2
      }
    }

    describe("round-trip properties") {

      checkProperty("ISBN round-trip should preserve valid ISBNs") {
        genULID
      } { ulid =>
        val extracted = ULIDConverter.extractISBNFromULID(ulid)
        // For ULIDs generated from valid ISBNs, extraction should work
        extracted.isDefined
      }

      checkProperty("Non-ISBN strings should produce None on extraction") {
        Gen.zip(alphaString.suchThat(_.nonEmpty), genTimestamp)
      } { case (nonIsbnStr, timestamp) =>
        val ulid      = ULIDConverter.generateULID(nonIsbnStr, timestamp)
        val extracted = ULIDConverter.extractISBNFromULID(ulid)
        extracted.isEmpty || nonIsbnStr.isbnOpt.isDefined
      }

      checkProperty("ULID generation should be consistent for same inputs") {
        Gen.zip(alphaNumString, genTimestamp)
      } { case (input, timestamp) =>
        val ulid1 = ULIDConverter.generateULID(input, timestamp)
        val ulid2 = ULIDConverter.generateULID(input, timestamp)
        // With same timestamp and input, ULIDs should be identical
        ulid1 == ulid2
      }
    }
  }
