package com.handybookshelf

import cats.effect.Async

package object controller {

  // Type alias for a function that takes an effect type F and a resource type R
  // and returns a value of type F with the resource R.
  // This is useful for defining asynchronous operations that involve resources.
  type _async[F[_], R] = F[R]

  // Uncomment the following line if you want to use a specific Async instance
  // for the effect type F, which can be useful for more complex operations.
  // This line requires an implicit Async instance for F.
//  type _async[F[_]: Async, R] = F |= R

}
