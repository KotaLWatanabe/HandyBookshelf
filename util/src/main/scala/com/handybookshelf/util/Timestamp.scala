package com.handybookshelf
package util

import cats.Eval
import com.handybookshelf.util.Timestamp.systemZoneId
import org.atnos.eff.*
import org.atnos.eff.all._eval

import java.time.{ZoneId, ZonedDateTime}

final case class Timestamp private (private val value: ZonedDateTime) extends AnyVal:
  private[util] def epochMillis: Long = value.toInstant.toEpochMilli
  override def toString: String       = value.toLocalDateTime.toString

  def plusHours(hours: Long): Timestamp =
    Timestamp(value.plusSeconds(hours * 3600))

  def isAfter(other: Timestamp): Boolean =
    value.isAfter(other.value)

object Timestamp:
  private[util] def apply(value: ZonedDateTime): Timestamp = new Timestamp(value)
  val systemZoneId: ZoneId                                 = ZoneId.of("Asia/Tokyo")
  val init: Timestamp                                      = Timestamp.fromEpochMillis(0L)

  def now: Timestamp = Timestamp(ZonedDateTime.now(systemZoneId))

  def fromEpochMillis(epochMillis: Long): Timestamp =
    Timestamp(
      ZonedDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epochMillis),
        systemZoneId
      )
    )

object TimestampGenerator:
  def now[R: _eval]: Eff[R, Timestamp] =
    Eff.send[Eval[*], R, Timestamp](Eval.later(Timestamp(ZonedDateTime.now(systemZoneId))))
