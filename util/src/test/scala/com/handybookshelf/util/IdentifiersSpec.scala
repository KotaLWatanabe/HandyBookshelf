package com.handybookshelf
package util

import org.scalacheck.Gen
import scala.annotation.nowarn
import ArxivId.*
import DOI.*

@nowarn("msg=unused")
class IdentifiersSpec extends PropertyBasedTestHelpers:

  // 有効なarXiv IDの例
  val validArxivIds: List[String] = List(
    "2301.12345",     // 標準形式
    "1706.03762",     // Attention is All You Need
    "2301.12345v1",   // バージョン1
    "2301.12345v12",  // 2桁バージョン
    "0704.0001",      // 古い形式
    "2312.1234",      // 4桁番号
    "2312.12345"      // 5桁番号
  )

  // 無効なarXiv IDの例
  val invalidArxivIds: List[String] = List(
    "arxiv:2301.12345", // プレフィックス付き
    "2301.123",         // 番号が短すぎる
    "2301.123456",      // 番号が長すぎる
    "230.12345",        // 年月が短い
    "23011.12345",      // 年月が長い
    "2301-12345",       // ハイフン区切り
    "2301.12345v",      // 不完全なバージョン
    "",                 // 空文字列
    "random"            // ランダムな文字列
  )

  // 有効なDOIの例
  val validDOIs: List[String] = List(
    "10.1000/xyz123",           // 基本形式
    "10.1038/nature12373",      // Nature
    "10.1109/5.771073",         // IEEE
    "10.1145/3442188.3445922",  // ACM
    "10.48550/arXiv.2301.12345", // arXiv DOI
    "10.1007/978-3-319-24574-4_28", // Springer章
    "10.1371/journal.pone.0000000"  // PLOS ONE
  )

  // 無効なDOIの例
  val invalidDOIs: List[String] = List(
    "doi:10.1000/xyz123", // プレフィックス付き
    "11.1000/xyz123",     // 10で始まらない
    "10.123/xyz",         // プレフィックスが短い
    "10.1000",            // サフィックスなし
    "10.1000/",           // 空のサフィックス
    "",                   // 空文字列
    "random"              // ランダムな文字列
  )

  // arXiv IDのジェネレータ
  def genValidArxivId: Gen[String] = for {
    year  <- Gen.choose(7, 99)
    month <- Gen.choose(1, 12)
    num   <- Gen.oneOf(
      Gen.choose(1000, 9999),
      Gen.choose(10000, 99999)
    )
    version <- Gen.option(Gen.choose(1, 20))
  } yield {
    val base = f"$year%02d$month%02d.$num"
    version.map(v => s"${base}v$v").getOrElse(base)
  }

  // DOIのジェネレータ
  def genValidDOI: Gen[String] = for {
    prefix <- Gen.choose(1000, 999999999)
    suffix <- Gen.nonEmptyListOf(Gen.oneOf(Gen.alphaNumChar, Gen.const('-'), Gen.const('_'), Gen.const('.'))).map(_.mkString)
  } yield s"10.$prefix/$suffix"

  describe("ArxivId") {

    describe("fromString") {

      it("should accept valid arXiv IDs") {
        validArxivIds.foreach { id =>
          withClue(s"For arXiv ID: $id") {
            ArxivId.fromString(id).isDefined shouldBe true
          }
        }
      }

      it("should reject invalid arXiv IDs") {
        invalidArxivIds.foreach { id =>
          withClue(s"For invalid arXiv ID: $id") {
            ArxivId.fromString(id).isDefined shouldBe false
          }
        }
      }

      checkProperty("should accept generated valid arXiv IDs") {
        genValidArxivId
      } { id =>
        ArxivId.fromString(id).isDefined shouldBe true
      }
    }

    describe("version extraction") {

      it("should extract version from versioned arXiv IDs") {
        ArxivId.fromString("2301.12345v1").flatMap(_.version) shouldBe Some(1)
        ArxivId.fromString("2301.12345v12").flatMap(_.version) shouldBe Some(12)
      }

      it("should return None for unversioned arXiv IDs") {
        ArxivId.fromString("2301.12345").flatMap(_.version) shouldBe None
      }
    }

    describe("withoutVersion") {

      it("should remove version from versioned arXiv IDs") {
        ArxivId.fromString("2301.12345v2").map(_.withoutVersion) shouldBe Some("2301.12345")
      }

      it("should return same value for unversioned arXiv IDs") {
        ArxivId.fromString("2301.12345").map(_.withoutVersion) shouldBe Some("2301.12345")
      }
    }

    describe("normalization") {

      it("should normalize versioned and unversioned IDs to same value") {
        val base = ArxivId.fromString("2301.12345").map(_.normalized)
        val versioned = ArxivId.fromString("2301.12345v2").map(_.normalized)
        base shouldBe versioned
      }
    }
  }

  describe("DOI") {

    describe("fromString") {

      it("should accept valid DOIs") {
        validDOIs.foreach { doi =>
          withClue(s"For DOI: $doi") {
            DOI.fromString(doi).isDefined shouldBe true
          }
        }
      }

      it("should reject invalid DOIs") {
        invalidDOIs.foreach { doi =>
          withClue(s"For invalid DOI: $doi") {
            DOI.fromString(doi).isDefined shouldBe false
          }
        }
      }

      checkProperty("should accept generated valid DOIs") {
        genValidDOI
      } { doi =>
        DOI.fromString(doi).isDefined shouldBe true
      }
    }

    describe("prefix and suffix") {

      it("should extract prefix correctly") {
        DOI.fromString("10.1038/nature12373").map(_.prefix) shouldBe Some("10.1038")
      }

      it("should extract suffix correctly") {
        DOI.fromString("10.1038/nature12373").map(_.suffix) shouldBe Some("nature12373")
      }

      it("should handle suffix with slashes") {
        DOI.fromString("10.1007/978-3-319-24574-4_28").map(_.suffix) shouldBe Some("978-3-319-24574-4_28")
      }
    }

    describe("normalization") {

      it("should normalize DOIs to lowercase") {
        val upper = DOI.fromString("10.1038/NATURE12373").map(_.normalized)
        val lower = DOI.fromString("10.1038/nature12373").map(_.normalized)
        upper shouldBe lower
      }
    }
  }

  describe("NES extension methods") {

    describe("arxivIdOpt") {

      it("should convert valid NES to ArxivId") {
        "2301.12345".nes.arxivIdOpt.isDefined shouldBe true
      }

      it("should return None for invalid arXiv ID") {
        "invalid".nes.arxivIdOpt.isDefined shouldBe false
      }
    }

    describe("doiOpt") {

      it("should convert valid NES to DOI") {
        "10.1038/nature12373".nes.doiOpt.isDefined shouldBe true
      }

      it("should return None for invalid DOI") {
        "invalid".nes.doiOpt.isDefined shouldBe false
      }
    }
  }
