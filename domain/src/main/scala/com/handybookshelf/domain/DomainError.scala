package com.handybookshelf
package domain

trait DomainError:
  def message: String
  def cause: Option[Throwable]

final case class BookIdError(cause: Option[Throwable]) extends DomainError:
  def message: String = "Failed to generate a BookId."

/** 重複する識別子で書籍を登録しようとした際のエラー */
final case class DuplicateBookError(identifier: BookIdentifier, existingBookId: BookId) extends DomainError:
  def message: String = s"Book with identifier '${identifier.description}' already exists (ID: $existingBookId)"
  def cause: Option[Throwable] = None

/** 識別子が不正な形式の場合のエラー */
final case class InvalidIdentifierError(identifierType: String, value: String, reason: String) extends DomainError:
  def message: String          = s"Invalid $identifierType: '$value' - $reason"
  def cause: Option[Throwable] = None
