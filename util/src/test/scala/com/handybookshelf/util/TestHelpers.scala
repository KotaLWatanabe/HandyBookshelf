package com.handybookshelf
package util

import org.scalacheck.Gen
import org.scalatest.Assertion
import org.scalatest.funspec.{AnyFunSpec, AsyncFunSpec}
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.Future

// 同期版プロパティテスト
trait PropertyBasedTestHelpers
    extends AnyFunSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with TestConfiguration
    with TestDataGenerators
    with GenUtils:

  def checkProperty[A](description: String)(gen: Gen[A])(fun: A => Assertion): Unit =
    if (sampleSize == 1)
      it(description) {
        checkPropertyMultiple(gen)(fun)
      }
    else
      it(s"$description (${sampleSize.toString}x samples)") {
        checkPropertyMultiple(gen)(fun)
      }

  private def checkPropertyMultiple[A](gen: Gen[A])(fun: A => Assertion): Assertion = {
    val samples = gen.sampleStream(sampleSize)
    samples.zipWithIndex.foreach { case (sample, index) =>
      withClue(s"Failed for sample #${index + 1}: ${sample.toString}") {
        fun(sample)
      }
    }
    succeed
  }

// 非同期版プロパティテスト
trait AsyncPropertyBasedTestHelpers
    extends AsyncFunSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with TestConfiguration
    with TestDataGenerators
    with GenUtils:

  def checkProperty[A](description: String)(gen: Gen[A])(funF: A => Future[Assertion]): Unit =
    if (sampleSize == 1)
      it(description) {
        checkPropertyMultiple(gen)(funF)
      }
    else
      it(s"$description (${sampleSize.toString}x samples)") {
        checkPropertyMultiple(gen)(funF)
      }

  private def checkPropertyMultiple[A](gen: Gen[A])(funF: A => Future[Assertion]): Future[Assertion] = {
    val samples = gen.sampleStream(sampleSize)
    samples.zipWithIndex.foldLeft(Future.successful(succeed)) { case (acc, (sample, index)) =>
      acc.flatMap { _ =>
        funF(sample).recover { case e: Throwable =>
          fail(s"Exception during property check for sample #${index + 1}: $sample", e)
        }
      }
    }
  }

trait TestConfiguration:
  val sampleSize: Int = sys.env.get("PROPERTY_TEST_SAMPLES").map(_.toInt).getOrElse(1) // CI用デフォルト
