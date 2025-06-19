package com.handybookshelf
package util

import cats.effect.IO

import java.time.{ZoneId, ZonedDateTime}

final case class Timestamp private (private val value: ZonedDateTime)
    extends AnyVal:
  private[util] def epochMillis: Long = value.toInstant.toEpochMilli
  override def toString: String = value.toLocalDateTime.toString

object Timestamp:
  private val systemZoneId: ZoneId = ZoneId.of("Asia/Tokyo")
  val init: Timestamp = Timestamp.fromEpochMillis(0L)
  def now: IO[Timestamp] =
    IO(ZonedDateTime.now(systemZoneId)).map(Timestamp.apply)
  def fromEpochMillis(epochMillis: Long): Timestamp =
    Timestamp(
      ZonedDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epochMillis),
        systemZoneId
      )
    )
