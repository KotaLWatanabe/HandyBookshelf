package com.handybookshelf package domain

import util.infrastructure.StoredEvent

trait StoredDomainEvent extends StoredEvent:
  override type E <: DomainEvent