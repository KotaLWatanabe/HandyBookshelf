package com.handybookshelf
package controller.api

import cats.data.Writer
import cats.effect.IO
import com.handybookshelf.adopter.AdopterError
import com.handybookshelf.usecase.UseCaseError
import org.atnos.eff.{Fx, |=}

package object routes {
  type EffectStack = Fx.fx4[IO, Either[AdopterError, *], Either[UseCaseError, *], Writer[String, *]]

  type _adopterEither[A] = Either[AdopterError, A]
  type _adopterError[R]  = _adopterEither |= R

}