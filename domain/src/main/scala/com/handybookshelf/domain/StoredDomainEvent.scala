package com.handybookshelf
package domain

import com.handybookshelf.infrastructure.StoredEvent

trait StoredDomainEvent extends StoredEvent:
  override type E <: DomainEvent