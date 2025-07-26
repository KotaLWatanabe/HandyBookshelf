package com.handybookshelf
package infrastructure

import cats.effect.{IO, Resource}
import org.atnos.eff.ExecutorServices

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}
import scala.concurrent.ExecutionContext

object ExecutionContexts {

  // Named thread factory
  private def namedThreadFactory(name: String): ThreadFactory = {
    val counter = new AtomicInteger(0)
    (r: Runnable) => {
      val thread = new Thread(r, s"$name-${counter.getAndIncrement()}")
      thread.setDaemon(true)
      thread.setUncaughtExceptionHandler((t, e) => {
        System.err.println(s"Uncaught exception in thread ${t.getName}: ${e.getMessage}")
        e.printStackTrace()
      })
      thread
    }
  }

  private def createExecutionContext(
      executorService: => ExecutorService
  ): Resource[IO, ExecutionContext] =
    Resource.make(
      IO.delay {
        ExecutionContext.fromExecutor(executorService)
      }
    )(ec =>
      IO.eval {
        val executorServices = ExecutorServices.fromExecutionContext(ec)
        executorServices.shutdown
      }
    )

  // Database operations pool
  private def databaseExecutionContext: Resource[IO, ExecutionContext] =
    createExecutionContext(Executors.newFixedThreadPool(10, namedThreadFactory("db-pool")))

  // HTTP client pool
  private def httpClientExecutionContext: Resource[IO, ExecutionContext] =
    createExecutionContext(Executors.newCachedThreadPool(namedThreadFactory("http-client")))

//  // Event processing pool
//  def eventProcessingExecutionContext: Resource[IO, ExecutionContext] =
//    Resource.make(
//      IO.delay {
//        val executor = Executors.newFixedThreadPool(
//          Runtime.getRuntime.availableProcessors(),
//          namedThreadFactory("event-processor")
//        )
//        ExecutionContext.fromExecutor(executor)
//      }
//    )(ec =>
//      IO.delay {
//        val executor = ec.asInstanceOf[ExecutionContext.fromExecutor].executor
//        executor.shutdown()
//        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
//          executor.shutdownNow()
//        }
//      }
//    )

  // Background tasks pool
  private def backgroundTaskExecutionContext: Resource[IO, ExecutionContext] =
    createExecutionContext(Executors.newFixedThreadPool(2, namedThreadFactory("background-task")))

//  // File I/O operations pool
//  def fileIOExecutionContext: Resource[IO, ExecutionContext] =
//    Resource.make(
//      IO.delay {
//        val executor = Executors.newFixedThreadPool(
//          4, // Limited for file operations
//          namedThreadFactory("file-io")
//        )
//        ExecutionContext.fromExecutor(executor)
//      }
//    )(ec =>
//      IO.delay {
//        val executor = ec.asInstanceOf[ExecutionContext.fromExecutor].executor
//        executor.shutdown()
//        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
//          executor.shutdownNow()
//        }
//      }
//    )

  // All execution contexts as a bundle
  final case class ExecutionContextBundle(
      database: ExecutionContext,
      httpClient: ExecutionContext,
      backgroundTask: ExecutionContext
  )

  def createExecutionContextBundle: Resource[IO, ExecutionContextBundle] =
    for {
      db         <- databaseExecutionContext
      http       <- httpClientExecutionContext
      background <- backgroundTaskExecutionContext
    } yield ExecutionContextBundle(db, http, background)
}

// Extension methods for easy ExecutionContext switching
extension [A](io: IO[A]) {
  def onDatabase(using contexts: ExecutionContexts.ExecutionContextBundle): IO[A] =
    io.evalOn(contexts.database)

  def onHttpClient(using contexts: ExecutionContexts.ExecutionContextBundle): IO[A] =
    io.evalOn(contexts.httpClient)

//  def onEventProcessing(using contexts: ExecutionContexts.ExecutionContextBundle): IO[A] =
//    io.evalOn(contexts.eventProcessing)

  def onBackgroundTask(using contexts: ExecutionContexts.ExecutionContextBundle): IO[A] =
    io.evalOn(contexts.backgroundTask)

//  def onFileIO(using contexts: ExecutionContexts.ExecutionContextBundle): IO[A] =
//    io.evalOn(contexts.fileIO)
}
