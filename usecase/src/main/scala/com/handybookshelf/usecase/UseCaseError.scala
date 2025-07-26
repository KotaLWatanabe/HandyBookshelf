package com.handybookshelf
package usecase

sealed trait UseCaseError extends Exception {
  def message: String
  override def getMessage: String = message
}

object UseCaseError {
  final case class ValidationError(message: String)      extends UseCaseError
  final case class NotFoundError(message: String)        extends UseCaseError
  final case class ExternalServiceError(message: String) extends UseCaseError
  final case class InternalError(message: String)        extends UseCaseError
  final case class TimeoutError(message: String)         extends UseCaseError
}
