package com.handybookshelf
package controller.actors

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.receptionist.{Receptionist, ServiceKey}
import org.apache.pekko.util.Timeout
import cats.syntax.all.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import domain.UserAccountId
import infrastructure.{SessionService, SessionId, LoginResult, LogoutResult, UserStatusResult, ValidationResult, ExtensionResult}
import scala.util.chaining.*
import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object SupervisorActor:
  sealed trait SupervisorCommand

  // User session management commands
  final case class LoginUser(
    userAccountId: UserAccountId,
    replyTo: ActorRef[LoginResult]
  ) extends SupervisorCommand
  
  final case class LogoutUser(
    userAccountId: UserAccountId,
    replyTo: ActorRef[LogoutResult]
  ) extends SupervisorCommand
  
  final case class GetUserStatus(
    userAccountId: UserAccountId,
    replyTo: ActorRef[UserStatusResult]
  ) extends SupervisorCommand

  
  // Administrative commands
  final case class GetChildren(replyTo: ActorRef[Set[String]]) extends SupervisorCommand
  case object Shutdown extends SupervisorCommand

  // Response types
  sealed trait SupervisorResponse

  val supervisorServiceKey: ServiceKey[SupervisorCommand] = ServiceKey("supervisor")

  def apply(sessionService: SessionService): Behavior[SupervisorCommand] =
    Behaviors.setup { context =>
      context.log.info("SupervisorActor starting with new architecture...")

      // Register this actor with the receptionist
      context.system.receptionist ! Receptionist.Register(
        supervisorServiceKey,
        context.self
      )

      supervising(
        sessionService = sessionService,
        children = Set.empty
      )
    }

  private def supervising(
    sessionService: SessionService,
    children: Set[String]
  ): Behavior[SupervisorCommand] =
    Behaviors
      .receive[SupervisorCommand] { (context, message) =>
        message match
          // Session management
          case LoginUser(userAccountId, replyTo) =>
            val result = sessionService.login(userAccountId).unsafeRunSync()
            replyTo ! result
            Behaviors.same

          case LogoutUser(userAccountId, replyTo) =>
            val result = sessionService.logout(userAccountId).unsafeRunSync()
            replyTo ! result
            Behaviors.same

          case GetUserStatus(userAccountId, replyTo) =>
            val result = sessionService.getUserStatus(userAccountId).unsafeRunSync()
            replyTo ! result
            Behaviors.same
            
          // Administrative
          case GetChildren(replyTo) =>
            replyTo ! children
            Behaviors.same

          case Shutdown =>
            context.log.info("SupervisorActor shutting down...")
            Behaviors.stopped
      }
      .receiveSignal { case (context, org.apache.pekko.actor.typed.Terminated(ref)) =>
        val childName = ref.path.name
        context.log.info(s"Child actor terminated: $childName")

        supervising(sessionService, children - childName)
      }

object SupervisorActorUtil:

  /**
   * Create and start the supervisor actor system
   */
  def createSupervisorSystem(sessionService: SessionService): ActorSystem[SupervisorActor.SupervisorCommand] =
    ActorSystem(SupervisorActor(sessionService), "HandyBookshelfSupervisor")
    