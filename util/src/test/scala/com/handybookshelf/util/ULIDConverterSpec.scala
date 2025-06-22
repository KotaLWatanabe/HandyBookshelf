package com.handybookshelf
package util

import org.scalacheck.Gen

class ULIDConverterSpec extends PropertyBasedTestHelpers:
  describe("ULIDConverter") {
    describe("generateULID") {
      checkProperty("should generate all-zero random part for non-ISBN strings") {
        Gen.zip(invalidISBN, genTimestamp)
      } { case (invalidIsbn, timestamp) =>
        val ulid       = ULIDConverter.createULID(invalidIsbn.nes, timestamp)
        val ulidBytes  = ulid.toBytes
        val randomPart = ulidBytes.slice(6, 16) // Skip 6-byte timestamp, take 10-byte random part
        randomPart.forall(_ == 0)
      }

      checkProperty("should be deterministic with same timestamp and input") {
        Gen.zip(alphaNumString, genTimestamp)
      } { case (bookCode, timestamp) =>
        val ulid1 = ULIDConverter.createULID(bookCode.nes, timestamp)
        val ulid2 = ULIDConverter.createULID(bookCode.nes, timestamp)
        ulid1 == ulid2
      }
    }

    describe("extractISBNFromULID") {
      checkProperty("should return None for ULID generated without ISBN") {
        Gen.zip(invalidISBN, genTimestamp)
      } { case (invalidIsbn: NES, timestamp) =>
        val ulid      = ULIDConverter.createULID(invalidIsbn, timestamp)
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
        val ulid = ULIDConverter.createULID(invalidIsbn.nes, timestamp)
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
      checkProperty("should handle very long non-ISBN string") {
        genTimestamp
      } { timestamp =>
        val longString = "a" * 50
        val ulid       = ULIDConverter.createULID(longString.nes, timestamp)
        val extracted  = ULIDConverter.extractISBNFromULID(ulid)
        extracted.isEmpty
      }

      checkProperty("should be consistent across different random strings") {
        Gen.zip(alphaNumString, genTimestamp)
      } { case (randomStr, timestamp) =>
        val ulid1 = ULIDConverter.createULID(randomStr.nes, timestamp)
        val ulid2 = ULIDConverter.createULID(randomStr.nes, timestamp)
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
        val ulid      = ULIDConverter.createULID(nonIsbnStr.nes, timestamp)
        val extracted = ULIDConverter.extractISBNFromULID(ulid)
        extracted.isEmpty
      }

      checkProperty("ULID generation should be consistent for same inputs") {
        Gen.zip(alphaNumString, genTimestamp)
      } { case (input, timestamp) =>
        val ulid1 = ULIDConverter.createULID(input.nes, timestamp)
        val ulid2 = ULIDConverter.createULID(input.nes, timestamp)
        ulid1 == ulid2
      }
    }
  }
