package com.handybookshelf.controller

import cats.effect.IO

final case class User(id: Int, name: String)

object UserLogic:
  private val users = Map(
    1 -> User(1, "Alice"),
    2 -> User(2, "Bob")
  )

  def getUser(id: Int): IO[Option[User]] = IO.pure(users.get(id))
