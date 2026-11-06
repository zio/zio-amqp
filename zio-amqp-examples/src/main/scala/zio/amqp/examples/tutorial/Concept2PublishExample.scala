//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.1
// Run from this folder:  scala-cli run Concept2PublishExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.tutorial.Concept2PublishExample"

package zio.amqp.examples.tutorial

import com.rabbitmq.client.MessageProperties
import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Work Queue Tutorial — 2. Publish Tasks
 *
 * Publishes five persistent task messages through the default exchange. Setup from section 1: the connection, the
 * channel and the durable queue.
 */
object Concept2PublishExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("tasks")

  val program: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.queueDeclare(queue, durable = true)
        _          <- ZIO.foreachDiscard(1 to 5) { i =>
                        channel.publish(
                          exchange = ExchangeName(""),
                          body = s"task-$i".getBytes("UTF-8"),
                          routingKey = RoutingKey(QueueName.unwrap(queue)),
                          props = MessageProperties.PERSISTENT_TEXT_PLAIN
                        )
                      }
        waiting    <- channel.messageCount(queue)
        _          <- Console.printLine(s"Messages waiting in the queue: $waiting")
      } yield ()
    }

  def run: ZIO[Any, Throwable, Unit] = program
}
