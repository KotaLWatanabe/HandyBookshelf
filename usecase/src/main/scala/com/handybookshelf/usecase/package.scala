package com.handybookshelf

import cats.effect.IO

package object usecase {

  // Simplified effect stack for compilation
  type UsecaseEff[A] = IO[Either[UseCaseError, A]]

  // Convenience methods for common operations
  def pure[A](value: A): UsecaseEff[A] =
    IO.pure(Right(value))

  def error[A](err: UseCaseError): UsecaseEff[A] =
    IO.pure(Left(err))

  def fromIOUsecase[A](io: IO[A]): UsecaseEff[A] =
    io.map(Right(_))

  def logInfo(message: String): UsecaseEff[Unit] =
    IO.println(s"INFO: $message").map(Right(_))

  def logError(message: String): UsecaseEff[Unit] =
    IO.println(s"ERROR: $message").map(Right(_))

  def getConfig: UsecaseEff[EffectStack.UseCaseConfig] =
    IO.pure(Right(EffectStack.UseCaseConfig(timeout = 5000, retryCount = 3)))
}
