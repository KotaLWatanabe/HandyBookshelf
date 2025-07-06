package com.handybookshelf
package domain

trait AggregateRoot[A <: AggregateRoot[A, E], E <: DomainEvent]:
  def id: String
  def version: EventVersion
  def uncommittedEvents: List[E]

  protected def applyEvent(event: E): A

  def withEvent(event: E): A =
    applyEvent(event)

  def markEventsAsCommitted: A

  def loadFromHistory(events: List[E]): A =
    events.foldLeft(this.asInstanceOf[A]) { (aggregate, event) =>
      aggregate.applyEvent(event)
    }

object AggregateRoot:
  def replayEvents[A <: AggregateRoot[A, E], E <: DomainEvent](
      initial: A,
      events: List[E]
  ): A = initial.loadFromHistory(events)
