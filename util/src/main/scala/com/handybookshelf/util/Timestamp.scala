package com.handybookshelf
package util

import org.atnos.eff.*

import java.time.{ZoneId, ZonedDateTime}

final case class Timestamp private (private val value: ZonedDateTime) extends AnyVal:
  private[util] def epochMillis: Long = value.toInstant.toEpochMilli
  override def toString: String       = value.toLocalDateTime.toString

object Timestamp:
  private[util] def apply(value: ZonedDateTime): Timestamp = new Timestamp(value)
  val systemZoneId: ZoneId = ZoneId.of("Asia/Tokyo")
  val init: Timestamp      = Timestamp.fromEpochMillis(0L)

  def fromEpochMillis(epochMillis: Long): Timestamp =
    Timestamp(
      ZonedDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epochMillis),
        systemZoneId
      )
    )

  import cats.effect.IO
  def now: IO[Timestamp] = IO(Timestamp(ZonedDateTime.now(systemZoneId)))

type CurrentDT[A] = A => Timestamp
object CurrentDT:
  import Timestamp.*
  def apply[A]: CurrentDT[A] = _ => Timestamp(ZonedDateTime.now(systemZoneId))

object CurrentDateTimeGenerator:
  type _current[R] = CurrentDT |= R
  def now[R: _current]: Eff[R, Timestamp] = Eff.send[CurrentDT[*], R, Timestamp](CurrentDT.apply)
