package com.handybookshelf
package domain

trait DomainError:
  def message: String
  def cause: Option[Throwable]

final case class BookIdError(cause: Option[Throwable]) extends DomainError:
  def message: String = "Failed to generate a BookId."
