package com.handybookshelf package domain

import com.handybookshelf.util.Timestamp
import wvlet.airframe.ulid.ULID

final case class DomainEventError(message: String, cause: Option[Throwable]) extends DomainError

final case class EventId private (private val value: ULID) extends AnyVal
object EventId:
  def generate(): EventId = EventId(ULID.newULID)
  
  def fromString(str: String): Either[DomainError, EventId] =
    try 
      Right(EventId(ULID.fromString(str)))
    catch 
      case e: Exception => Left(DomainEventError(s"Invalid EventId format: $str", Some(e)))

final case class EventVersion(value: Long) extends AnyVal:
  def next: EventVersion = EventVersion(value + 1)

object EventVersion:
  val initial: EventVersion = EventVersion(1L)

trait DomainEvent:
  def eventId: EventId
  def aggregateId: String
  def version: EventVersion  
  def timestamp: Timestamp
  def eventType: String
