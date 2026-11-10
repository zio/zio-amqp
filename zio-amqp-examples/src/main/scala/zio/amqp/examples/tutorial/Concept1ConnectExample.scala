//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.2
// Run from this folder:  scala-cli run Concept1ConnectExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.tutorial.Concept1ConnectExample"

package zio.amqp.examples.tutorial

import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Work Queue Tutorial — 1. Connect and Declare a Queue
 *
 * Opens a connection and a channel as scoped resources and declares a durable queue.
 */
object Concept1ConnectExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("tasks")

  val program: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        name       <- channel.queueDeclare(queue, durable = true)
        _          <- Console.printLine(s"Declared queue: $name")
      } yield ()
    }

  def run: ZIO[Any, Throwable, Unit] = program
}
