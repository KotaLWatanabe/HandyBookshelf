package com.handybookshelf
package usecase

sealed trait UsecaseError extends Exception {
  def message: String
  override def getMessage: String = message
}

object UsecaseError {
  final case class ValidationError(message: String) extends UsecaseError
  final case class NotFoundError(message: String) extends UsecaseError
  final case class ExternalServiceError(message: String) extends UsecaseError
  final case class InternalError(message: String) extends UsecaseError
  final case class TimeoutError(message: String) extends UsecaseError
}