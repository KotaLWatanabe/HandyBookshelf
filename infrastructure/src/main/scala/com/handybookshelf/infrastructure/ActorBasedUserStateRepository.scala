package com.handybookshelf
package infrastructure

import cats.effect.IO
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.util.Timeout
import scala.concurrent.duration.*
import com.handybookshelf.domain.UserAccountId
import com.handybookshelf.infrastructure.actors.UserStateActor

class ActorBasedUserStateRepository(
  userStateActor: ActorRef[UserStateActor.UserStateMessage]
)(using actorSystem: ActorSystem[?]) extends UserStateRepository {
  
  private given Timeout = Timeout(5.seconds)
  
  def executeCommand(command: UserCommand): IO[Unit] = {
    val askResult = userStateActor.ask[UserStateActor.CommandResult] { replyTo =>
      UserStateActor.ProcessCommand(command, replyTo)
    }
    
    IO.fromFuture(IO.pure(askResult)).flatMap {
      case UserStateActor.CommandResult.CommandSuccess =>
        IO.unit
      case UserStateActor.CommandResult.CommandFailure(error) =>
        IO.raiseError(new RuntimeException(s"Command failed: $error"))
    }
  }
  
  def getUserState(userAccountId: UserAccountId): IO[UserQueryResult] = {
    val askResult = userStateActor.ask[UserQueryResult] { replyTo =>
      UserStateActor.GetUserState(userAccountId, replyTo)
    }
    
    IO.fromFuture(IO.pure(askResult))
  }
  
  def getAllActiveUsers(): IO[List[UserState]] = {
    val askResult = userStateActor.ask[List[UserState]] { replyTo =>
      UserStateActor.GetAllActiveUsers(replyTo)
    }
    
    IO.fromFuture(IO.pure(askResult))
  }
  
  def getUsersLoggedInAfter(timestamp: Long): IO[List[UserState]] = {
    val askResult = userStateActor.ask[List[UserState]] { replyTo =>
      UserStateActor.GetUsersLoggedInAfter(timestamp, replyTo)
    }
    
    IO.fromFuture(IO.pure(askResult))
  }
  
  def isHealthy(): IO[Boolean] = {
    val askResult = userStateActor.ask[Boolean] { replyTo =>
      UserStateActor.HealthCheck(replyTo)
    }
    
    IO.fromFuture(IO.pure(askResult))
  }
}

object ActorBasedUserStateRepository {
  
  def create()(using actorSystem: ActorSystem[?]): IO[ActorBasedUserStateRepository] = {
    val userStateActor = actorSystem.systemActorOf(
      UserStateActor(),
      "user-state-actor"
    )
    
    IO.pure(new ActorBasedUserStateRepository(userStateActor))
  }
  
  def createWithActor(
    userStateActor: ActorRef[UserStateActor.UserStateMessage]
  )(using actorSystem: ActorSystem[?]): ActorBasedUserStateRepository = {
    new ActorBasedUserStateRepository(userStateActor)
  }
}