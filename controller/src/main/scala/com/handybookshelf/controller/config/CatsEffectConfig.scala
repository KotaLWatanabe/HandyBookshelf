package handybookshelf.com.handybookshelf
package controller.config

import cats.effect.{IO, Resource}
import cats.effect.kernel.Async
import cats.effect.std.{Queue, Supervisor}
import scala.concurrent.duration.*
import java.util.concurrent.{Executors, ThreadFactory}
import scala.concurrent.ExecutionContext

/**
 * Cats Effect configuration for resource management and execution contexts
 */
object CatsEffectConfig:
  
  /**
   * Default thread pool configuration
   */
  object ThreadPoolConfig:
    val corePoolSize: Int = Runtime.getRuntime.availableProcessors()
    val maxPoolSize: Int = Runtime.getRuntime.availableProcessors() * 2
    val keepAliveTime: FiniteDuration = 60.seconds
    val queueSize: Int = 1000
  
  /**
   * HTTP server configuration
   */
  object HttpServerConfig:
    val host: String = "0.0.0.0"
    val port: Int = 8080
    val maxConnections: Int = 1024
    val requestTimeout: FiniteDuration = 30.seconds
    val idleTimeout: FiniteDuration = 60.seconds
    val shutdownTimeout: FiniteDuration = 10.seconds
  
  /**
   * Database connection pool configuration (for future use)
   */
  object DatabaseConfig:
    val maxConnections: Int = 10
    val connectionTimeout: FiniteDuration = 30.seconds
    val idleTimeout: FiniteDuration = 10.minutes
    val maxLifetime: FiniteDuration = 30.minutes
    val leakDetectionThreshold: FiniteDuration = 60.seconds
  
  /**
   * Event processing configuration
   */
  object EventProcessingConfig:
    val batchSize: Int = 100
    val maxWaitTime: FiniteDuration = 5.seconds
    val parallelism: Int = Runtime.getRuntime.availableProcessors()
    val bufferSize: Int = 1000
  
  /**
   * Retry configuration
   */
  object RetryConfig:
    val maxRetries: Int = 3
    val initialDelay: FiniteDuration = 1.second
    val maxDelay: FiniteDuration = 30.seconds
    val backoffFactor: Double = 2.0
    val jitter: Boolean = true
  
  /**
   * Circuit breaker configuration
   */
  object CircuitBreakerConfig:
    val maxFailures: Int = 5
    val resetTimeout: FiniteDuration = 30.seconds
    val exponentialBackoffFactor: Double = 2.0
    val maxResetTimeout: FiniteDuration = 5.minutes
  
  /**
   * Create a custom thread factory for named threads
   */
  def namedThreadFactory(name: String): ThreadFactory =
    new ThreadFactory:
      private val counter = new java.util.concurrent.atomic.AtomicInteger(0)
      def newThread(r: Runnable): Thread =
        val thread = new Thread(r, s"$name-${counter.incrementAndGet()}")
        thread.setDaemon(true)
        thread
  
  /**
   * Create a bounded execution context resource
   */
  def boundedExecutionContext(name: String, poolSize: Int): Resource[IO, ExecutionContext] =
    Resource.make(
      IO.delay {
        val executor = Executors.newFixedThreadPool(poolSize, namedThreadFactory(name))
        (executor, ExecutionContext.fromExecutor(executor))
      }
    )(pair => IO.delay(pair._1.shutdown()).void).map(_._2)
  
  /**
   * Create a supervisor resource for managing background tasks
   */
  def supervisorResource[F[_]: Async]: Resource[F, Supervisor[F]] =
    Supervisor[F]
  
  /**
   * Create a bounded queue resource
   */
  def boundedQueue[F[_]: Async, A](capacity: Int): F[Queue[F, A]] =
    Queue.bounded[F, A](capacity)
  
  /**
   * Timeout configuration for various operations
   */
  object TimeoutConfig:
    val httpRequest: FiniteDuration = 30.seconds
    val databaseQuery: FiniteDuration = 10.seconds
    val actorAsk: FiniteDuration = 5.seconds
    val eventProcessing: FiniteDuration = 60.seconds
    val gracefulShutdown: FiniteDuration = 30.seconds