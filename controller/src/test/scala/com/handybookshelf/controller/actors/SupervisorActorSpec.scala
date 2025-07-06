package com.handybookshelf.controller.actors

import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import com.handybookshelf.domain.UserAccountId
import org.scalatest.wordspec.AnyWordSpecLike
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration.*

class SupervisorActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike:

  "SupervisorActor" should {
    
    "create and return a UserAccountActor for a given UserAccountId" in {
      val supervisorActor = spawn(SupervisorActor())
      val probe = createTestProbe[SupervisorActor.UserActorResponse]()
      
      val userAccountId = UserAccountId.create().unsafeRunSync()
      
      supervisorActor ! SupervisorActor.GetUserActor(userAccountId, probe.ref)
      
      val response = probe.receiveMessage(3.seconds)
      response shouldBe a[SupervisorActor.UserActorRef]
      
      val userActorRef = response.asInstanceOf[SupervisorActor.UserActorRef].actorRef
      userActorRef should not be null
    }
    
    "return the same UserAccountActor for the same UserAccountId" in {
      val supervisorActor = spawn(SupervisorActor())
      val probe = createTestProbe[SupervisorActor.UserActorResponse]()
      
      val userAccountId = UserAccountId.create().unsafeRunSync()
      
      supervisorActor ! SupervisorActor.GetUserActor(userAccountId, probe.ref)
      val firstResponse = probe.receiveMessage(3.seconds)
      val firstActorRef = firstResponse.asInstanceOf[SupervisorActor.UserActorRef].actorRef
      
      supervisorActor ! SupervisorActor.GetUserActor(userAccountId, probe.ref)
      val secondResponse = probe.receiveMessage(3.seconds)
      val secondActorRef = secondResponse.asInstanceOf[SupervisorActor.UserActorRef].actorRef
      
      firstActorRef shouldEqual secondActorRef
    }
    
    "create different UserAccountActors for different UserAccountIds" in {
      val supervisorActor = spawn(SupervisorActor())
      val probe = createTestProbe[SupervisorActor.UserActorResponse]()
      
      val userAccountId1 = UserAccountId.create().unsafeRunSync()
      val userAccountId2 = UserAccountId.create().unsafeRunSync()
      
      supervisorActor ! SupervisorActor.GetUserActor(userAccountId1, probe.ref)
      val firstResponse = probe.receiveMessage(3.seconds)
      val firstActorRef = firstResponse.asInstanceOf[SupervisorActor.UserActorRef].actorRef
      
      supervisorActor ! SupervisorActor.GetUserActor(userAccountId2, probe.ref)
      val secondResponse = probe.receiveMessage(3.seconds)
      val secondActorRef = secondResponse.asInstanceOf[SupervisorActor.UserActorRef].actorRef
      
      firstActorRef should not equal secondActorRef
    }
    
    "interact with created UserAccountActor" in {
      val supervisorActor = spawn(SupervisorActor())
      val supervisorProbe = createTestProbe[SupervisorActor.UserActorResponse]()
      val userProbe = createTestProbe[UserAccountActor.UserAccountResponse]()
      
      val userAccountId = UserAccountId.create().unsafeRunSync()
      
      supervisorActor ! SupervisorActor.GetUserActor(userAccountId, supervisorProbe.ref)
      val response = supervisorProbe.receiveMessage(3.seconds)
      val userActorRef = response.asInstanceOf[SupervisorActor.UserActorRef].actorRef
      
      userActorRef ! UserAccountActor.GetUserStatus(userAccountId, userProbe.ref)
      val statusResponse = userProbe.receiveMessage(3.seconds)
      
      statusResponse shouldBe a[UserAccountActor.UserStatusResponse]
      val status = statusResponse.asInstanceOf[UserAccountActor.UserStatusResponse]
      status.userAccountId shouldEqual userAccountId
      status.isLoggedIn shouldBe false
    }
  }