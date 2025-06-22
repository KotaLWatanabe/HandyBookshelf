package com.handybookshelf
package util

class ISBNSpec extends PropertyBasedTestHelpers:
  describe("ISBNSpec") {
    describe("ISBN validation examples") {
      checkProperty("valid ISBN should always be accepted")(genValidISBN) { isbn =>
        isbn.matches("\\d{10}|\\d{13}")
      }

      checkProperty("invalid ISBN should be rejected")(invalidISBN) { isbn =>
        !isbn.matches("\\d{10}|\\d{13}")
      }

      checkPropertyMultiple("multiple ISBN validation", count = 5)(genValidISBN) { isbn =>
        isbn.length == 10 || isbn.length == 13
      }
    }

    describe("Manual sampling examples") {
      it("direct sampling with extension method") {
        val isbn = genValidISBN.sampleOne
        isbn should (have length 10 or have length 13)
      }

      it("sampling multiple values") {
        val isbns = genValidISBN.sampleStream(3)
        isbns.foreach { isbn =>
          isbn should (have length 10 or have length 13)
        }
      }

      it("optional sampling") {
        val maybeIsbn = genValidISBN.sampleOption
        maybeIsbn should be(defined)
      }
    }
  }

  describe("Utility string generators") {

    checkProperty("alpha string should contain only letters")(alphaString) { str =>
      str.nonEmpty && str.matches("[a-zA-Z]+")
    }

    checkProperty("alphanumeric string should be formatted")(alphaNumString) { str =>
      str.nonEmpty && str.matches("[a-zA-Z0-9]+")
    }

    checkProperty("numeric string should contain only digits")(numericString) { str =>
      str.matches("\\d+")
    }
  }
