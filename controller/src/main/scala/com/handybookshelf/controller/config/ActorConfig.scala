package com.handybookshelf 
package controller
package config

import scala.concurrent.duration.*

/**
 * Pekko Actor system configuration
 */
object ActorConfig:
  
  /**
   * Default actor system configuration
   */
  val defaultActorSystemName: String = "HandyBookshelfActorSystem"
  
  /**
   * Default timeout for actor operations
   */
  val defaultTimeout: FiniteDuration = 30.seconds
  
  /**
   * Default dispatcher configuration
   */
  val defaultDispatcher: String = "pekko.actor.default-dispatcher"
  
  /**
   * Actor system shutdown timeout
   */
  val shutdownTimeout: FiniteDuration = 10.seconds
  
  /**
   * Supervision strategy configuration
   */
  object SupervisionConfig:
    val maxRetries: Int = 3
    val retryWindow: FiniteDuration = 1.minute
    val backoffMinDuration: FiniteDuration = 1.second
    val backoffMaxDuration: FiniteDuration = 10.seconds
    val backoffRandomFactor: Double = 0.2
  
  /**
   * User account actor configuration
   */
  object UserAccountActorConfig:
    val idleTimeout: FiniteDuration = 30.minutes
    val maxInactiveTime: FiniteDuration = 1.hour
    val passivationTimeout: FiniteDuration = 5.minutes
  
  /**
   * Cluster configuration (for future use)
   */
  object ClusterConfig:
    val seedNodes: List[String] = List("pekko://HandyBookshelfActorSystem@127.0.0.1:2551")
    val port: Int = 2551
    val roles: Set[String] = Set("backend")