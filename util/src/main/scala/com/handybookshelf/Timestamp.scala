package com.handybookshelf

import java.time.{ZoneId, ZonedDateTime}

final case class Timestamp private (private val value: ZonedDateTime):
  def epochMillis: Long = value.toInstant.toEpochMilli

  override def toString: String = value.toLocalDateTime.toString

object Timestamp:
  private val systemZoneId: ZoneId = ZoneId.of("JST")
  def now(): Timestamp = Timestamp(ZonedDateTime.now(systemZoneId))
