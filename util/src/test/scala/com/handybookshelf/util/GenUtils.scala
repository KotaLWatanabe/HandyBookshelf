package com.handybookshelf.util

import org.scalacheck.Gen

trait GenUtils:

  extension [A](gen: Gen[A])
    def sampleOne: A =
      gen.sample match
        case Some(value) => value
        case None        => throw new IllegalStateException(s"Failed to generate sample from generator")

    def sampleStream(count: Int): List[A] = 
      Gen.listOfN(count, gen).sampleOne

  given [A] => Conversion[A, Gen[A]]:
    def apply(a: A): Gen[A] = Gen.const[A](a)
