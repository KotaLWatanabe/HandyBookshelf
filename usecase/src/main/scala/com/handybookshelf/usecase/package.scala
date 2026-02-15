package com.handybookshelf

import cats.data.{RWS, Writer}
import cats.effect.IO
import org.atnos.eff.addon.cats.effect.IOEffect
import org.atnos.eff.*

package object usecase:
  type EffectStack = Fx.fx3[IO, Either[UseCaseError, *], Writer[String, *]]

  type _UsecaseEither[A] = Either[UseCaseError, A]
  type _usecaseError[R]  = _UsecaseEither |= R

  type _writer[R] = Writer[String, *] |= R

  // Convenience methods for common operations
  def pure[A](value: A): Eff[EffectStack, A] =
    IOEffect.fromIO(IO.pure(value))

  def error[A](err: UseCaseError): Eff[EffectStack, A] =
    EitherEffect.fromEither(Left(err))

  def fromIOUsecase[A](io: IO[A]): Eff[EffectStack, A] =
    IOEffect.fromIO(io)

  def fromEither[A](either: Either[UseCaseError, A]): Eff[EffectStack, A] =
    EitherEffect.fromEither(either)

  def logInfo(message: String): Eff[EffectStack, Unit] = ???

  def logError(message: String): Eff[EffectStack, Unit] = ???
