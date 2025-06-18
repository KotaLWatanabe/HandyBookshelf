package com.handybookshelf

import java.time.{ZoneId, ZonedDateTime}

final case class Timestamp private (private val value: ZonedDateTime) extends AnyVal:
  def epochMillis: Long = value.toInstant.toEpochMilli
  override def toString: String = value.toLocalDateTime.toString

object Timestamp:
  private val systemZoneId: ZoneId = ZoneId.of("Asia/Tokyo")
  val init: Timestamp              = Timestamp.fromEpochMillis(0L)
  def now(): Timestamp             = Timestamp(ZonedDateTime.now(systemZoneId))
  def fromEpochMillis(epochMillis: Long): Timestamp =
    Timestamp(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), systemZoneId))
