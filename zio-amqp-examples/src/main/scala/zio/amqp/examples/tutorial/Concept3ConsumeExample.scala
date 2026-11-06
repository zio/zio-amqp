//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.1
// Run from this folder:  scala-cli run Concept3ConsumeExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.tutorial.Concept3ConsumeExample"

package zio.amqp.examples.tutorial

import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Work Queue Tutorial — 3. Consume and Acknowledge
 *
 * Consumes up to five tasks from the queue and acknowledges each one after it is handled. Run Concept2PublishExample
 * first so the queue has messages.
 */
object Concept3ConsumeExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("tasks")

  val program: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel
                        .consume(queue, ConsumerTag("worker"))
                        .mapZIO { delivery =>
                          val task = new String(delivery.getBody, "UTF-8")
                          Console.printLine(s"Working on $task") *>
                            channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
                        }
                        .take(5)
                        .runDrain
      } yield ()
    }

  def run: ZIO[Any, Throwable, Unit] = program
}
