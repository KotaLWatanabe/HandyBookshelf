package com.handybookshelf

import org.scalacheck.Gen
import org.scalatest.funspec.{AnyFunSpec, AsyncFunSpec}
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future
import GenUtils.*
import ISBN.*

trait PropertyBasedTestHelpers extends AnyFunSpec with Matchers:

  // 同期版プロパティテスト
  def checkProperty[A](description: String)(gen: Gen[A])(predicate: A => Boolean): Unit =
    it(description) {
      val sample = gen.sampleOne
      withClue(s"Failed for generated value: $sample") {
        predicate(sample).shouldBe(true)
      }
    }

  def checkPropertyWithSeed[A](description: String, seed: Long)(gen: Gen[A])(predicate: A => Boolean): Unit =
    it(s"$description (seed: $seed)") {
      val sample = gen.sampleWithSeed(seed)
      withClue(s"Failed for generated value: $sample (seed: $seed)") {
        predicate(sample).shouldBe(true)
      }
    }

  def checkPropertyMultiple[A](description: String, count: Int = 10)(gen: Gen[A])(predicate: A => Boolean): Unit =
    it(s"$description (${count}x samples)") {
      val samples = gen.sampleStream(count)
      samples.zipWithIndex.foreach { case (sample, index) =>
        withClue(s"Failed for sample #${index + 1}: $sample") {
          predicate(sample).shouldBe(true)
        }
      }
    }

  def checkBinaryProperty[A, B](description: String)(genA: Gen[A])(genB: Gen[B])(predicate: (A, B) => Boolean): Unit =
    it(description) {
      val sampleA = genA.sampleOne
      val sampleB = genB.sampleOne
      withClue(s"Failed for generated values: A=$sampleA, B=$sampleB") {
        predicate(sampleA, sampleB).shouldBe(true)
      }
    }

  def expectGenerated[A](description: String)(gen: Gen[A])(expectation: A => Unit): Unit =
    it(description) {
      val sample = gen.sampleOne
      withClue(s"Generated value: $sample") {
        expectation(sample)
      }
    }

trait AsyncPropertyBasedTestHelpers extends AsyncFunSpec with Matchers:
  // 非同期版プロパティテスト
  def checkPropertyAsync[A](description: String)(gen: Gen[A])(predicate: A => Future[Boolean]): Unit =
    it(description) {
      val sample = gen.sampleOne
      predicate(sample).map { result =>
        withClue(s"Failed for generated value: $sample") {
          result.shouldBe(true)
        }
      }
    }

  def checkPropertyWithSeedAsync[A](description: String, seed: Long)(
      gen: Gen[A]
  )(predicate: A => Future[Boolean]): Unit =
    it(s"$description (seed: $seed)") {
      val sample = gen.sampleWithSeed(seed)
      predicate(sample).map { result =>
        withClue(s"Failed for generated value: $sample (seed: $seed)") {
          result.shouldBe(true)
        }
      }
    }

  def checkPropertyMultipleAsync[A](description: String, count: Int = 10)(
      gen: Gen[A]
  )(predicate: A => Future[Boolean]): Unit =
    it(s"$description (${count}x samples)") {
      val samples = gen.sampleStream(count)
      val futures = samples.zipWithIndex.map { case (sample, index) =>
        predicate(sample).map { result =>
          withClue(s"Failed for sample #${index + 1}: $sample") {
            result.shouldBe(true)
          }
        }
      }
      Future.sequence(futures).map(_ => succeed)
    }

  def checkBinaryPropertyAsync[A, B](
      description: String
  )(genA: Gen[A])(genB: Gen[B])(predicate: (A, B) => Future[Boolean]): Unit =
    it(description) {
      val sampleA = genA.sampleOne
      val sampleB = genB.sampleOne
      predicate(sampleA, sampleB).map { result =>
        withClue(s"Failed for generated values: A=$sampleA, B=$sampleB") {
          result.shouldBe(true)
        }
      }
    }

  def expectGeneratedAsync[A](description: String)(gen: Gen[A])(expectation: A => Future[Unit]): Unit =
    it(description) {
      val sample = gen.sampleOne
      withClue(s"Generated value: $sample") {
        expectation(sample).map(_ => succeed)
      }
    }

object TestDataGenerators:

  def nonEmptyString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)

  def shortString: Gen[String] = Gen.listOfN(10, Gen.alphaNumChar).map(_.mkString)

  def isbnDigits10: Gen[String] = Gen.listOfN(10, Gen.numChar).map(_.mkString)

  def isbnDigits13: Gen[String] = Gen.listOfN(13, Gen.numChar).map(_.mkString)

  def genValidISBN: Gen[ISBN] = Gen.oneOf(isbnDigits10, isbnDigits13).map(_.isbnOpt.get)

  def genTimestamp: Gen[Timestamp]        = Gen.choose(0L, System.currentTimeMillis()).map(Timestamp.fromEpochMillis)
  def genCurrentTimestamp: Gen[Timestamp] = Gen.const(Timestamp.now())
  def genTimestampInRange(start: Timestamp = Timestamp.init, end: Timestamp): Gen[Timestamp] =
    Gen.choose(start.epochMillis, end.epochMillis).map(Timestamp.fromEpochMillis)

  val invalidISBN: Gen[String] = Gen.oneOf(
    Gen.listOfN(9, Gen.numChar).map(_.mkString),      // 9 digits
    Gen.listOfN(11, Gen.numChar).map(_.mkString),     // 11 digits
    Gen.listOfN(12, Gen.numChar).map(_.mkString),     // 12 digits
    Gen.listOfN(14, Gen.numChar).map(_.mkString),     // 14 digits
    Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString) // non-digits
  )

  val positiveInt: Gen[Int] = Gen.posNum[Int]

  val smallPositiveInt: Gen[Int] = Gen.choose(1, 100)

  val timestamp: Gen[Long] = Gen.choose(0L, System.currentTimeMillis())

  val alphaString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  val alphaNumString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)

  val numericString: Gen[String] = Gen.nonEmptyListOf(Gen.numChar).map(_.mkString)
