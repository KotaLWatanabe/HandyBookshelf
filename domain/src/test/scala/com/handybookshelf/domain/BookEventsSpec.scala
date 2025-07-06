package com.handybookshelf.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class BookEventsSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  "BookRegistered" should "contain valid book information" in {
    pending
  }

  "BookLocationChanged" should "contain valid location information" in {
    pending
  }

  "BookTagAdded" should "contain valid tag information" in {
    pending
  }

  "BookTagRemoved" should "contain valid tag information" in {
    pending
  }

  "BookUnregistered" should "contain valid unregistration information" in {
    pending
  }

  "Event serialization" should "roundtrip correctly" in {
    pending
  }
}