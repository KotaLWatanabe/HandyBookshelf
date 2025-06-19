package com.handybookshelf
package controller.actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}

object SupervisorActor:
  sealed trait SupervisorCommand

  final case class StartChildActor(name: String) extends SupervisorCommand
  final case class StopChildActor(name: String) extends SupervisorCommand
  final case class GetChildren(replyTo: ActorRef[Set[String]])
      extends SupervisorCommand
  case object Shutdown extends SupervisorCommand

  val supervisorServiceKey: ServiceKey[SupervisorCommand] = ServiceKey(
    "supervisor"
  )

  def apply(): Behavior[SupervisorCommand] =
    Behaviors.setup { context =>
      context.log.info("SupervisorActor starting...")

      // Register this actor with the receptionist
      context.system.receptionist ! Receptionist.Register(
        supervisorServiceKey,
        context.self
      )

      supervising(Set.empty)
    }

  private def supervising(children: Set[String]): Behavior[SupervisorCommand] =
    Behaviors
      .receive[SupervisorCommand] { (context, message) =>
        message match
          case StartChildActor(name) =>
            if (children.contains(name)) {
              context.log.warn(s"Child actor with name '$name' already exists")
              Behaviors.same
            } else {
              // Create a simple behavior for demonstration
              val childBehavior = Behaviors.receiveMessage[String] { msg =>
                context.log.info(s"Child $name received: $msg")
                Behaviors.same
              }
              val childRef = context.spawn(childBehavior, name)
              context.watch(childRef)
              context.log.info(s"Started child actor: $name")
              supervising(children + name)
            }

          case StopChildActor(name) =>
            context.children.find(_.path.name == name) match
              case Some(childRef) =>
                context.stop(childRef)
                context.log.info(s"Stopped child actor: $name")
                supervising(children - name)
              case None =>
                context.log.warn(s"Child actor with name '$name' not found")
                Behaviors.same

          case GetChildren(replyTo) =>
            replyTo ! children
            Behaviors.same

          case Shutdown =>
            context.log.info("SupervisorActor shutting down...")
            Behaviors.stopped
      }
      .receiveSignal { case (context, akka.actor.typed.Terminated(ref)) =>
        val childName = ref.path.name
        context.log.info(s"Child actor terminated: $childName")
        supervising(children - childName)
      }

/** Supervisor Actor utility methods
  */
object SupervisorActorUtil:

  /** Create and start the supervisor actor system
    */
  def createSupervisorSystem(): ActorSystem[SupervisorActor.SupervisorCommand] =
    ActorSystem(SupervisorActor(), "HandyBookshelfSupervisor")
