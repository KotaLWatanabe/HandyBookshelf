package com.handybookshelf
package infrastructure.actors

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import com.handybookshelf.domain.UserAccountId
import com.handybookshelf.infrastructure.{UserCommand, UserState, UserQueryResult}

object UserStateActor:
  
  // Actor messages
  sealed trait UserStateMessage
  
  // Command messages (state changes)
  final case class ProcessCommand(
    command: UserCommand,
    replyTo: ActorRef[CommandResult]
  ) extends UserStateMessage
  
  // Query messages (state retrieval) 
  final case class GetUserState(
    userAccountId: UserAccountId,
    replyTo: ActorRef[UserQueryResult]
  ) extends UserStateMessage
  
  final case class GetAllActiveUsers(
    replyTo: ActorRef[List[UserState]]
  ) extends UserStateMessage
  
  final case class GetUsersLoggedInAfter(
    timestamp: Long,
    replyTo: ActorRef[List[UserState]]
  ) extends UserStateMessage
  
  final case class HealthCheck(
    replyTo: ActorRef[Boolean]
  ) extends UserStateMessage
  
  // Response types
  sealed trait CommandResult
  case object CommandSuccess extends CommandResult
  final case class CommandFailure(error: String) extends CommandResult

  def apply(): Behavior[UserStateMessage] =
    Behaviors.setup { context =>
      context.log.info("UserStateActor starting...")
      
      active(Map.empty[UserAccountId, UserState])
    }

  private def active(
    userStates: Map[UserAccountId, UserState]
  ): Behavior[UserStateMessage] =
    Behaviors.receive { (context, message) =>
      message match {
        case ProcessCommand(command, replyTo) =>
          try {
            val newStates = processCommand(command, userStates)
            replyTo ! CommandSuccess
            active(newStates)
          } catch {
            case ex: Exception =>
              context.log.error(s"Error processing command: ${ex.getMessage}")
              replyTo ! CommandFailure(ex.getMessage)
              Behaviors.same
          }
          
        case GetUserState(userAccountId, replyTo) =>
          val result = userStates.get(userAccountId) match {
            case Some(state) => UserQueryResult(found = true, state = Some(state))
            case None => UserQueryResult(found = false, state = None)
          }
          replyTo ! result
          Behaviors.same
          
        case GetAllActiveUsers(replyTo) =>
          val activeUsers = userStates.values.filter(_.isLoggedIn).toList
          replyTo ! activeUsers
          Behaviors.same
          
        case GetUsersLoggedInAfter(timestamp, replyTo) =>
          val filteredUsers = userStates.values.filter { state =>
            state.isLoggedIn && state.lastActivity.exists(_ > timestamp)
          }.toList
          replyTo ! filteredUsers
          Behaviors.same
          
        case HealthCheck(replyTo) =>
          replyTo ! true
          Behaviors.same
      }
    }
  
  private def processCommand(
    command: UserCommand, 
    currentStates: Map[UserAccountId, UserState]
  ): Map[UserAccountId, UserState] = {
    import com.handybookshelf.infrastructure.{LoginUserCommand, LogoutUserCommand, UpdateUserActivityCommand}
    
    command match {
      case LoginUserCommand(userId, sessionId) =>
        val currentState = currentStates.getOrElse(userId, UserState(
          userAccountId = userId,
          isLoggedIn = false,
          currentSessionId = None,
          lastActivity = None,
          loginCount = 0
        ))
        
        val newState = currentState.copy(
          isLoggedIn = true,
          currentSessionId = Some(sessionId),
          lastActivity = Some(System.currentTimeMillis()),
          loginCount = currentState.loginCount + 1
        )
        
        currentStates.updated(userId, newState)
        
      case LogoutUserCommand(userId, _) =>
        currentStates.get(userId) match {
          case Some(currentState) =>
            val newState = currentState.copy(
              isLoggedIn = false,
              currentSessionId = None,
              lastActivity = Some(System.currentTimeMillis())
            )
            currentStates.updated(userId, newState)
          case None =>
            currentStates
        }
        
      case UpdateUserActivityCommand(userId, sessionId, timestamp) =>
        currentStates.get(userId) match {
          case Some(currentState) if currentState.currentSessionId.contains(sessionId) =>
            val newState = currentState.copy(
              lastActivity = Some(timestamp)
            )
            currentStates.updated(userId, newState)
          case _ =>
            currentStates
        }
    }
  }