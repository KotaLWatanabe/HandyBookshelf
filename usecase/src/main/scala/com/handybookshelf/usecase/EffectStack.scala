package com.handybookshelf
package usecase

import cats.effect.IO
import org.atnos.eff.Fx

object EffectStack {

  case class UseCaseConfig(
      timeout: Long,
      retryCount: Int
  )

  type EffectStack = Fx.fx3[IO, Either[UseCaseError, *], Writer[String, *]]

  // Simplified effect type for compilation
  type UseCaseOperation[A] = IO[Either[String, A]]

  // Error handling helpers
  def fail[A](error: String): UseCaseOperation[A] =
    IO.pure(Left(error))

  def success[A](value: A): UseCaseOperation[A] =
    IO.pure(Right(value))

  // IO operation helper
  def fromIO[A](io: IO[A]): UseCaseOperation[A] =
    io.map(Right(_))

  // Configuration access (simplified)
  def withConfig[A](config: UseCaseConfig)(
      operation: UseCaseConfig => UseCaseOperation[A]
  ): UseCaseOperation[A] =
    operation(config)

  // Runner for usecase operations
  def runUseCase[A](
      operation: UseCaseOperation[A],
      config: UseCaseConfig
  ): IO[Either[String, A]] = {
    val _ = config // Acknowledge the parameter to avoid warning
    operation
  }

  // Runner with logging (simplified)
  def runUseCaseWithLog[A](
      operation: UseCaseOperation[A],
      config: UseCaseConfig
  ): IO[(List[String], Either[String, A])] = {
    val _ = config // Acknowledge the parameter to avoid warning
    operation.map(result => (List.empty[String], result))
  }
}
