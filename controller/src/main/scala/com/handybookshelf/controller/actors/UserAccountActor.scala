package com.handybookshelf package controller.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import domain.UserAccountId

object UserAccountActor:
  sealed trait UserAccountCommand

  final case class LoginUser(
    userAccountId: UserAccountId,
    replyTo: ActorRef[LoginResponse]
  ) extends UserAccountCommand
  
  final case class LogoutUser(
    userAccountId: UserAccountId,
    replyTo: ActorRef[LogoutResponse]
  ) extends UserAccountCommand
  
  final case class GetUserStatus(
    userAccountId: UserAccountId,
    replyTo: ActorRef[UserStatusResponse]
  ) extends UserAccountCommand
  
  case object Shutdown extends UserAccountCommand
  
  // Response types
  sealed trait UserAccountResponse
  final case class LoginResponse(success: Boolean, message: String) extends UserAccountResponse
  final case class LogoutResponse(success: Boolean, message: String) extends UserAccountResponse
  final case class UserStatusResponse(userAccountId: UserAccountId, isLoggedIn: Boolean) extends UserAccountResponse
  
  def apply(userAccountId: UserAccountId): Behavior[UserAccountCommand] =
    Behaviors.setup { context =>
      context.log.info(s"UserAccountActor starting for user: ${userAccountId.toString}")
      
      loggedOut(userAccountId)
    }
  
  private def loggedOut(userAccountId: UserAccountId): Behavior[UserAccountCommand] =
    Behaviors.receive { (context, message) =>
      message match
        case LoginUser(userId, replyTo) if userId == userAccountId =>
          context.log.info(s"User ${userId.toString} logging in")
          replyTo ! LoginResponse(success = true, s"User ${userId.toString} logged in successfully")
          loggedIn(userAccountId)
        
        case LoginUser(userId, replyTo) =>
          context.log.warn(s"Login attempt for wrong user ID: ${userId.toString}, expected: ${userAccountId.toString}")
          replyTo ! LoginResponse(success = false, "Invalid user ID")
          Behaviors.same
        
        case LogoutUser(_, replyTo) =>
          context.log.info(s"User ${userAccountId.toString} already logged out")
          replyTo ! LogoutResponse(success = true, s"User ${userAccountId.toString} was already logged out")
          Behaviors.same
        
        case GetUserStatus(_, replyTo) =>
          replyTo ! UserStatusResponse(userAccountId, isLoggedIn = false)
          Behaviors.same
        
        case Shutdown =>
          context.log.info(s"UserAccountActor for ${userAccountId.toString} shutting down")
          Behaviors.stopped
    }
  
  private def loggedIn(userAccountId: UserAccountId): Behavior[UserAccountCommand] =
    Behaviors.receive { (context, message) =>
      message match
        case LoginUser(userId, replyTo) if userId == userAccountId =>
          context.log.info(s"User ${userId.toString} already logged in")
          replyTo ! LoginResponse(success = true, s"User ${userId.toString} was already logged in")
          Behaviors.same
        
        case LoginUser(userId, replyTo) =>
          context.log.warn(s"Login attempt for wrong user ID: ${userId.toString}, expected: ${userAccountId.toString}")
          replyTo ! LoginResponse(success = false, "Invalid user ID")
          Behaviors.same
        
        case LogoutUser(userId, replyTo) if userId == userAccountId =>
          context.log.info(s"User ${userId.toString} logging out")
          replyTo ! LogoutResponse(success = true, s"User ${userId.toString} logged out successfully")
          loggedOut(userAccountId)
        
        case LogoutUser(userId, replyTo) =>
          context.log.warn(s"Logout attempt for wrong user ID: ${userId.toString}, expected: ${userAccountId.toString}")
          replyTo ! LogoutResponse(success = false, "Invalid user ID")
          Behaviors.same
        
        case GetUserStatus(_, replyTo) =>
          replyTo ! UserStatusResponse(userAccountId, isLoggedIn = true)
          Behaviors.same
        
        case Shutdown =>
          context.log.info(s"UserAccountActor for ${userAccountId.toString} shutting down")
          Behaviors.stopped
    }

/**
 * UserAccount Actor utility methods
 */
object UserAccountActorUtil:
  
  /**
   * Generate a unique actor name for a user account
   */
  def generateActorName(userAccountId: UserAccountId): String =
    s"user-account-${userAccountId.toString}"

object ActUseCase:
  sealed trait UseCaseType
  case object ShowBooks extends UseCaseType

  def run[P, O](useCaseType: UseCaseType, params: P): Unit = useCaseType match {
    case ShowBooks =>
      // Implement logic to show books
      println(s"Showing books with params: $params")
  }