package com.handybookshelf.util

import org.scalacheck.Gen
import org.scalacheck.rng.Seed

import scala.util.Random

trait GenUtils:

  extension [A](gen: Gen[A])
    def sampleOne: A =
      gen.sample match
        case Some(value) => value
        case None        => throw new IllegalStateException(s"Failed to generate sample from generator")

    def sampleWithSeed(seed: Long): A =
      val params = Gen.Parameters.default.withSize(100)
      gen.apply(params, Seed(seed)) match
        case Some(value) => value
        case None        => throw new IllegalStateException(s"Failed to generate sample from generator with seed $seed")

    def sampleOption: Option[A] = gen.sample

    def sampleStream(count: Int): List[A] =
      (1 to count).map(_ => gen.sampleOne).toList

    def sampleRandomSeed: A =
      val seed = Random.nextLong()
      gen.sampleWithSeed(seed)

  def sampleFrom[A](gen: Gen[A]): A = gen.sampleOne

  def sampleFromWithSeed[A](gen: Gen[A], seed: Long): A = gen.sampleWithSeed(seed)

  def sampleOptionalFrom[A](gen: Gen[A]): Option[A] = gen.sampleOption

  def sampleListFrom[A](gen: Gen[A], count: Int): List[A] = gen.sampleStream(count)
