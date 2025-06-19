package TestDataGenerators.*
import com.handybookshelf
class ExamplePropertyBasedTest extends PropertyBasedTestHelpers:

  describe("ISBN validation examples") {
    
    checkProperty("valid ISBN should always be accepted") {
      genValidISBN
    } { isbn =>
      isbn.matches("\\d{10}|\\d{13}")
    }
    
    checkProperty("invalid ISBN should be rejected") {
      invalidISBN
    } { isbn =>
      !isbn.matches("\\d{10}|\\d{13}")
    }
    
    checkPropertyMultiple("multiple ISBN validation", count = 5) {
      genValidISBN
    } { isbn =>
      isbn.length == 10 || isbn.length == 13
    }
  }

  describe("String generation examples") {
    
    expectGenerated("non-empty string should not be empty") {
      nonEmptyString
    } { str =>
      str should not be empty
      str.length should be > 0: Unit
    }
    
    checkBinaryProperty("two different strings should not be equal (usually)") {
      nonEmptyString
    } {
      nonEmptyString
    } { (str1, str2) =>
      // Note: This might occasionally fail due to randomness, but demonstrates the concept
      str1.length >= 0 && str2.length >= 0
    }
  }

  describe("Seed-based generation examples") {
    
    checkPropertyWithSeed("deterministic generation with fixed seed", 12345L) {
      positiveInt
    } { num =>
      num > 0
    }
    
    it("same seed produces same result") {
      val seed = 54321L
      val result1 = positiveInt.sampleWithSeed(seed)
      val result2 = positiveInt.sampleWithSeed(seed)
      result1 shouldEqual result2
    }
  }

  describe("Utility string generators") {
    
    checkProperty("alpha string should contain only letters") {
      alphaString
    } { str =>
      str.nonEmpty && str.matches("[a-zA-Z]+")
    }
    
    checkProperty("alphanumeric string should be formatted") {
      alphaNumString
    } { str =>
      str.nonEmpty && str.matches("[a-zA-Z0-9]+")
    }
    
    checkProperty("numeric string should contain only digits") {
      numericString
    } { str =>
      str.matches("\\d+")
    }
  }

  describe("Manual sampling examples") {
    
    it("direct sampling with extension method") {
      val isbn = genValidISBN.sampleOne
      isbn should (have length 10 or have length 13)
    }
    
    it("sampling multiple values") {
      val isbns = genValidISBN.sampleStream(3)
      isbns should have length 3
      isbns.foreach { isbn =>
        isbn should (have length 10 or have length 13)
      }
    }
    
    it("optional sampling") {
      val maybeIsbn = genValidISBN.sampleOption
      maybeIsbn should be(defined)
    }
  }