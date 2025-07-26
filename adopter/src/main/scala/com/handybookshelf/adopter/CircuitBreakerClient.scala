package com.handybookshelf
package adopter

import cats.effect.{IO, Ref, Temporal}
import cats.syntax.all.*
import scala.concurrent.duration.*

// Circuit breaker states
sealed trait CircuitBreakerState
case object Closed extends CircuitBreakerState
case object Open extends CircuitBreakerState  
case object HalfOpen extends CircuitBreakerState

// Circuit breaker configuration
final case class CircuitBreakerConfig(
    failureThreshold: Int = 5,
    recoveryTimeout: Duration = 30.seconds,
    halfOpenMaxCalls: Int = 3,
    callTimeout: Duration = 10.seconds
)

// Circuit breaker statistics
final case class CircuitBreakerStats(
    totalCalls: Long = 0,
    successfulCalls: Long = 0,
    failedCalls: Long = 0,
    consecutiveFailures: Int = 0,
    lastFailureTime: Option[Long] = None,
    state: CircuitBreakerState = Closed
)

// Circuit breaker errors
sealed trait CircuitBreakerError extends Exception
case object CircuitBreakerOpenError extends CircuitBreakerError {
  override def getMessage: String = "Circuit breaker is open - calls not allowed"
}
case object CircuitBreakerTimeoutError extends CircuitBreakerError {
  override def getMessage: String = "Circuit breaker call timeout"
}

// Circuit breaker implementation
class CircuitBreaker[F[_]: Temporal] private (
    config: CircuitBreakerConfig,
    statsRef: Ref[F, CircuitBreakerStats]
) {
  
  def execute[A](operation: F[A]): F[Either[CircuitBreakerError, A]] = {
    for {
      currentStats <- statsRef.get
      result <- currentStats.state match {
        case Closed =>
          executeInClosedState(operation)
        case Open =>
          executeInOpenState(operation)
        case HalfOpen =>
          executeInHalfOpenState(operation)
      }
    } yield result
  }
  
  def getStats: F[CircuitBreakerStats] = statsRef.get
  
  def reset: F[Unit] = statsRef.set(CircuitBreakerStats())
  
  private def executeInClosedState[A](operation: F[A]): F[Either[CircuitBreakerError, A]] = {
    executeWithTimeout(operation).flatMap {
      case Right(result) =>
        recordSuccess() *> Temporal[F].pure(Right(result))
      case Left(error) =>
        recordFailure().flatMap { shouldOpen =>
          if (shouldOpen) {
            transitionToOpen() *> Temporal[F].pure(Left(error))
          } else {
            Temporal[F].pure(Left(error))
          }
        }
    }
  }
  
  private def executeInOpenState[A](operation: F[A]): F[Either[CircuitBreakerError, A]] = {
    for {
      currentTime <- Temporal[F].realTime.map(_.toMillis)
      stats <- statsRef.get
      result <- stats.lastFailureTime match {
        case Some(lastFailure) if (currentTime - lastFailure) >= config.recoveryTimeout.toMillis =>
          transitionToHalfOpen() *> executeInHalfOpenState(operation)
        case _ =>
          Temporal[F].pure(Left(CircuitBreakerOpenError))
      }
    } yield result
  }
  
  private def executeInHalfOpenState[A](operation: F[A]): F[Either[CircuitBreakerError, A]] = {
    for {
      stats <- statsRef.get
      result <- if (stats.totalCalls < config.halfOpenMaxCalls) {
        executeWithTimeout(operation).flatMap {
          case Right(result) =>
            recordSuccess().flatMap { _ =>
              checkTransitionToClosed() *> Temporal[F].pure(Right(result))
            }
          case Left(error) =>
            recordFailure() *> transitionToOpen() *> Temporal[F].pure(Left(error))
        }
      } else {
        Temporal[F].pure(Left(CircuitBreakerOpenError))
      }
    } yield result
  }
  
  private def executeWithTimeout[A](operation: F[A]): F[Either[CircuitBreakerError, A]] = {
    Temporal[F].timeout(operation, config.callTimeout)
      .map(Right(_))
      .handleError(_ => Left(CircuitBreakerTimeoutError))
  }
  
  private def recordSuccess(): F[Unit] = {
    statsRef.update { stats =>
      stats.copy(
        totalCalls = stats.totalCalls + 1,
        successfulCalls = stats.successfulCalls + 1,
        consecutiveFailures = 0
      )
    }
  }
  
  private def recordFailure(): F[Boolean] = {
    for {
      currentTime <- Temporal[F].realTime.map(_.toMillis)
      shouldOpen <- statsRef.modify { stats =>
        val newStats = stats.copy(
          totalCalls = stats.totalCalls + 1,
          failedCalls = stats.failedCalls + 1,
          consecutiveFailures = stats.consecutiveFailures + 1,
          lastFailureTime = Some(currentTime)
        )
        val shouldTransition = newStats.consecutiveFailures >= config.failureThreshold
        (newStats, shouldTransition)
      }
    } yield shouldOpen
  }
  
  private def transitionToOpen(): F[Unit] = {
    statsRef.update(_.copy(state = Open))
  }
  
  private def transitionToHalfOpen(): F[Unit] = {
    statsRef.update(_.copy(state = HalfOpen, totalCalls = 0))
  }
  
  private def checkTransitionToClosed(): F[Unit] = {
    statsRef.get.flatMap { stats =>
      if (stats.successfulCalls >= config.halfOpenMaxCalls) {
        statsRef.update(_.copy(state = Closed, consecutiveFailures = 0))
      } else {
        Temporal[F].unit
      }
    }
  }
}

object CircuitBreaker {
  
  def create[F[_]: Temporal](config: CircuitBreakerConfig): F[CircuitBreaker[F]] = {
    Ref.of[F, CircuitBreakerStats](CircuitBreakerStats()).map { statsRef =>
      new CircuitBreaker(config, statsRef)
    }
  }
  
  def withDefaults[F[_]: Temporal]: F[CircuitBreaker[F]] = {
    create(CircuitBreakerConfig())
  }
}

// Enhanced external API client with circuit breaker
class CircuitBreakerApiClient(
    underlying: ExternalApiClient,
    circuitBreaker: CircuitBreaker[IO]
) extends ExternalApiClient {
  
  import io.circe.{Decoder, Encoder}
  
  def get[R: Decoder](
    path: String,
    queryParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]] = {
    circuitBreaker.execute(underlying.get[R](path, queryParams, headers)).flatMap {
      case Right(result) => IO.pure(result)
      case Left(CircuitBreakerOpenError) => 
        IO.pure(Left(ExternalApiError.NetworkError("Service temporarily unavailable (circuit breaker open)")))
      case Left(CircuitBreakerTimeoutError) => 
        IO.pure(Left(ExternalApiError.TimeoutError("Request timeout")))
    }
  }
  
  def post[T: Encoder, R: Decoder](
    path: String,
    body: T,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]] = {
    circuitBreaker.execute(underlying.post[T, R](path, body, headers)).flatMap {
      case Right(result) => IO.pure(result)
      case Left(CircuitBreakerOpenError) => 
        IO.pure(Left(ExternalApiError.NetworkError("Service temporarily unavailable (circuit breaker open)")))
      case Left(CircuitBreakerTimeoutError) => 
        IO.pure(Left(ExternalApiError.TimeoutError("Request timeout")))
    }
  }
  
  def put[T: Encoder, R: Decoder](
    path: String,
    body: T,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]] = {
    circuitBreaker.execute(underlying.put[T, R](path, body, headers)).flatMap {
      case Right(result) => IO.pure(result)
      case Left(CircuitBreakerOpenError) => 
        IO.pure(Left(ExternalApiError.NetworkError("Service temporarily unavailable (circuit breaker open)")))
      case Left(CircuitBreakerTimeoutError) => 
        IO.pure(Left(ExternalApiError.TimeoutError("Request timeout")))
    }
  }
  
  def delete[R: Decoder](
    path: String,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, R]] = {
    circuitBreaker.execute(underlying.delete[R](path, headers)).flatMap {
      case Right(result) => IO.pure(result)
      case Left(CircuitBreakerOpenError) => 
        IO.pure(Left(ExternalApiError.NetworkError("Service temporarily unavailable (circuit breaker open)")))
      case Left(CircuitBreakerTimeoutError) => 
        IO.pure(Left(ExternalApiError.TimeoutError("Request timeout")))
    }
  }
  
  def getRaw(
    path: String,
    queryParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, String]] = {
    circuitBreaker.execute(underlying.getRaw(path, queryParams, headers)).flatMap {
      case Right(result) => IO.pure(result)
      case Left(CircuitBreakerOpenError) => 
        IO.pure(Left(ExternalApiError.NetworkError("Service temporarily unavailable (circuit breaker open)")))
      case Left(CircuitBreakerTimeoutError) => 
        IO.pure(Left(ExternalApiError.TimeoutError("Request timeout")))
    }
  }
  
  def postRaw(
    path: String,
    body: String,
    headers: Map[String, String] = Map.empty
  ): IO[Either[ExternalApiError, String]] = {
    circuitBreaker.execute(underlying.postRaw(path, body, headers)).flatMap {
      case Right(result) => IO.pure(result)
      case Left(CircuitBreakerOpenError) => 
        IO.pure(Left(ExternalApiError.NetworkError("Service temporarily unavailable (circuit breaker open)")))
      case Left(CircuitBreakerTimeoutError) => 
        IO.pure(Left(ExternalApiError.TimeoutError("Request timeout")))
    }
  }
  
  def healthCheck(): IO[Boolean] = {
    underlying.healthCheck()
  }
  
  // Additional methods for circuit breaker monitoring
  def getCircuitBreakerStats: IO[CircuitBreakerStats] = circuitBreaker.getStats
  
  def resetCircuitBreaker: IO[Unit] = circuitBreaker.reset
}

object CircuitBreakerApiClient {
  
  def wrap(
    underlying: ExternalApiClient,
    config: CircuitBreakerConfig = CircuitBreakerConfig()
  ): IO[CircuitBreakerApiClient] = {
    CircuitBreaker.create[IO](config).map { cb =>
      new CircuitBreakerApiClient(underlying, cb)
    }
  }
}