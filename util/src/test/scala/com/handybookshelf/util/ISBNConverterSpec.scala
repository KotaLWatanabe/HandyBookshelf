package com.handybookshelf
package util

import org.scalacheck.Gen
import scala.annotation.nowarn
import ISBN.*

@nowarn("msg=unused")
class ISBNConverterSpec extends PropertyBasedTestHelpers:

  // 実際の書籍のISBNペア（ISBN-10, ISBN-13）
  val knownISBNPairs: List[(String, String)] = List(
    ("4873115655", "9784873115658"), // リーダブルコード
    ("4873117526", "9784873117522"), // プログラミングRust
    ("4873118220", "9784873118222"), // 入門 監視
    ("4798121967", "9784798121963"), // エリック・エヴァンスのドメイン駆動設計
    ("4048930508", "9784048930505"), // 実践ドメイン駆動設計
    ("0321125215", "9780321125217"), // Domain-Driven Design (原著)
    ("0596517742", "9780596517748"), // JavaScript: The Good Parts
    ("0201633612", "9780201633610")  // Design Patterns (GoF)
  )

  // ヘルパーメソッド：文字列からISBNを生成
  def toISBN(str: String): ISBN = str.nesOpt.flatMap(_.isbnOpt).get

  describe("ISBNConverter") {

    describe("calculateISBN13CheckDigit") {

      it("should calculate correct check digit for known ISBN-13s") {
        knownISBNPairs.foreach { case (_, isbn13) =>
          val withoutCheck = isbn13.take(12)
          val expected     = isbn13.last
          ISBNConverter.calculateISBN13CheckDigit(withoutCheck) shouldBe expected
        }
      }

      checkProperty("should always produce a single digit (0-9)") {
        Gen.listOfN(12, Gen.numChar).map(_.mkString)
      } { isbn12 =>
        val checkDigit = ISBNConverter.calculateISBN13CheckDigit(isbn12)
        checkDigit.isDigit shouldBe true
      }
    }

    describe("calculateISBN10CheckDigit") {

      it("should calculate correct check digit for known ISBN-10s") {
        knownISBNPairs.foreach { case (isbn10, _) =>
          val withoutCheck = isbn10.take(9)
          val expected     = isbn10.last
          ISBNConverter.calculateISBN10CheckDigit(withoutCheck) shouldBe expected
        }
      }

      checkProperty("should produce digit or X") {
        Gen.listOfN(9, Gen.numChar).map(_.mkString)
      } { isbn9 =>
        val checkDigit = ISBNConverter.calculateISBN10CheckDigit(isbn9)
        (checkDigit.isDigit || checkDigit == 'X') shouldBe true
      }
    }

    describe("convertISBN10to13") {

      it("should convert known ISBN-10s to ISBN-13s correctly") {
        knownISBNPairs.foreach { case (isbn10, isbn13) =>
          ISBNConverter.convertISBN10to13(isbn10).map(_.toString) shouldBe Some(isbn13)
        }
      }

      it("should return None for invalid input") {
        assert(ISBNConverter.convertISBN10to13("123").isEmpty)           // too short
        assert(ISBNConverter.convertISBN10to13("12345678901").isEmpty)   // too long
        assert(ISBNConverter.convertISBN10to13("abcdefghij").isEmpty)    // non-digits
      }

      checkProperty("should always produce 13-digit result for valid 10-digit input") {
        Gen.listOfN(10, Gen.numChar).map(_.mkString)
      } { isbn10 =>
        ISBNConverter.convertISBN10to13(isbn10) match {
          case Some(isbn13) => isbn13.length shouldBe 13
          case None         => fail(s"Conversion failed for: $isbn10")
        }
      }

      checkProperty("should always start with 978 prefix") {
        Gen.listOfN(10, Gen.numChar).map(_.mkString)
      } { isbn10 =>
        ISBNConverter.convertISBN10to13(isbn10) match {
          case Some(isbn13) => isbn13.startsWith("978") shouldBe true
          case None         => fail(s"Conversion failed for: $isbn10")
        }
      }
    }

    describe("convertISBN13to10") {

      it("should convert known ISBN-13s back to ISBN-10s correctly") {
        knownISBNPairs.foreach { case (isbn10, isbn13) =>
          ISBNConverter.convertISBN13to10(isbn13).map(_.toString) shouldBe Some(isbn10)
        }
      }

      it("should return None for 979 prefix ISBN-13") {
        // 979プレフィックスはISBN-10に変換できない
        ISBNConverter.convertISBN13to10("9791234567890") shouldBe None
      }

      it("should return None for invalid input") {
        assert(ISBNConverter.convertISBN13to10("123").isEmpty)           // too short
        assert(ISBNConverter.convertISBN13to10("12345678901234").isEmpty) // too long
        assert(ISBNConverter.convertISBN13to10("abcdefghijklm").isEmpty)  // non-digits
      }
    }

    describe("validate") {

      it("should validate known ISBN-10s") {
        knownISBNPairs.foreach { case (isbn10, _) =>
          ISBNConverter.validate(isbn10) shouldBe true
        }
      }

      it("should validate known ISBN-13s") {
        knownISBNPairs.foreach { case (_, isbn13) =>
          ISBNConverter.validate(isbn13) shouldBe true
        }
      }

      it("should reject invalid check digits") {
        // 最後の桁を変更して不正なチェックディジットにする
        assert(!ISBNConverter.validate("4873115656")) // 正しくは 4873115655
        assert(!ISBNConverter.validate("9784873115659")) // 正しくは 9784873115658
      }

      it("should reject invalid lengths") {
        assert(!ISBNConverter.validate("123456789"))   // 9 digits
        assert(!ISBNConverter.validate("12345678901")) // 11 digits
        assert(!ISBNConverter.validate("123456789012")) // 12 digits
      }
    }

    describe("round-trip conversion") {

      checkProperty("ISBN-10 -> ISBN-13 -> ISBN-10 should return original") {
        // 有効なチェックディジットを持つISBN-10を生成
        Gen.listOfN(9, Gen.numChar).map(_.mkString)
      } { isbn9 =>
        val checkDigit = ISBNConverter.calculateISBN10CheckDigit(isbn9)
        // X付きのISBN-10はこのテストでは除外（Iron制約が数字のみ）
        if (checkDigit != 'X') {
          val isbn10 = isbn9 + checkDigit
          val result = for {
            isbn13     <- ISBNConverter.convertISBN10to13(isbn10)
            backTo10   <- ISBNConverter.convertISBN13to10(isbn13)
          } yield backTo10.toString
          result shouldBe Some(isbn10)
        } else {
          succeed // X付きはスキップ
        }
      }
    }
  }

  describe("ISBN extension methods") {

    describe("isISBN10 / isISBN13") {

      it("should correctly identify ISBN-10") {
        val isbn10 = toISBN("4873115655")
        assert(isbn10.isISBN10)
        assert(!isbn10.isISBN13)
      }

      it("should correctly identify ISBN-13") {
        val isbn13 = toISBN("9784873115658")
        assert(!isbn13.isISBN10)
        assert(isbn13.isISBN13)
      }
    }

    describe("toISBN13") {

      it("should convert ISBN-10 to ISBN-13") {
        val isbn10 = toISBN("4873115655")
        isbn10.toISBN13.toString shouldBe "9784873115658"
      }

      it("should return same value for ISBN-13") {
        val isbn13 = toISBN("9784873115658")
        isbn13.toISBN13 shouldBe isbn13
      }
    }

    describe("normalized") {

      it("should normalize ISBN-10 and ISBN-13 to same value") {
        val isbn10 = toISBN("4873115655")
        val isbn13 = toISBN("9784873115658")

        isbn10.normalized shouldBe isbn13.normalized
      }
    }
  }

  describe("NormalizedISBN") {

    describe("fromISBN") {

      it("should normalize ISBN-10 to ISBN-13 format") {
        val isbn10 = toISBN("4873115655")
        NormalizedISBN.fromISBN(isbn10).value shouldBe "9784873115658"
      }

      it("should keep ISBN-13 as-is") {
        val isbn13 = toISBN("9784873115658")
        NormalizedISBN.fromISBN(isbn13).value shouldBe "9784873115658"
      }
    }

    describe("fromString") {

      it("should parse valid ISBN-10 string") {
        NormalizedISBN.fromString("4873115655").map(_.value) shouldBe Some("9784873115658")
      }

      it("should parse valid ISBN-13 string") {
        NormalizedISBN.fromString("9784873115658").map(_.value) shouldBe Some("9784873115658")
      }

      it("should return None for invalid ISBN") {
        assert(NormalizedISBN.fromString("invalid").isEmpty)
        assert(NormalizedISBN.fromString("123").isEmpty)
        assert(NormalizedISBN.fromString("").isEmpty)
      }
    }

    describe("equality") {

      it("should treat ISBN-10 and corresponding ISBN-13 as equal when normalized") {
        knownISBNPairs.foreach { case (isbn10Str, isbn13Str) =>
          val normalized10 = NormalizedISBN.fromString(isbn10Str)
          val normalized13 = NormalizedISBN.fromString(isbn13Str)

          (normalized10, normalized13) match {
            case (Some(n10), Some(n13)) =>
              n10.value shouldBe n13.value
            case _ =>
              fail(s"Failed to normalize: $isbn10Str or $isbn13Str")
          }
        }
      }
    }
  }

  describe("ISBNConverter.isSameBook") {

    it("should return true for ISBN-10 and its corresponding ISBN-13") {
      knownISBNPairs.foreach { case (isbn10Str, isbn13Str) =>
        val isbn10 = toISBN(isbn10Str)
        val isbn13 = toISBN(isbn13Str)

        assert(ISBNConverter.isSameBook(isbn10, isbn13))
        assert(ISBNConverter.isSameBook(isbn13, isbn10))
      }
    }

    it("should return true for same ISBN") {
      val isbn = toISBN("4873115655")
      ISBNConverter.isSameBook(isbn, isbn) shouldBe true
    }

    it("should return false for different books") {
      val isbn1 = toISBN("4873115655")   // リーダブルコード
      val isbn2 = toISBN("4873117526")   // プログラミングRust

      ISBNConverter.isSameBook(isbn1, isbn2) shouldBe false
    }

    checkProperty("should be reflexive") {
      genValidISBN
    } { isbn =>
      ISBNConverter.isSameBook(isbn, isbn) shouldBe true
    }

    checkProperty("should be symmetric") {
      Gen.zip(genValidISBN, genValidISBN)
    } { case (isbn1, isbn2) =>
      ISBNConverter.isSameBook(isbn1, isbn2) shouldBe ISBNConverter.isSameBook(isbn2, isbn1)
    }
  }
