//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.2
// Run from this folder:  scala-cli run PrefetchExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.PrefetchExample"

package zio.amqp.examples.howto

import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Limit Prefetch to Apply Back-Pressure
 *
 * A consumer with a prefetch of five that never acknowledges holds exactly five of twenty messages.
 */
object PrefetchExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("howto-prefetch")

  def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.queueDeclare(queue)
        _          <- channel.purgeQueue(queue)
        _          <- ZIO.foreachDiscard(1 to 20) { i =>
                        channel.publish(ExchangeName(""), s"message-$i".getBytes("UTF-8"), RoutingKey(QueueName.unwrap(queue)))
                      }
        _          <- channel.basicQos(5)
        fiber      <- channel.consume(queue, ConsumerTag("prefetch-example")).runDrain.fork
        _          <- ZIO.sleep(1.second)
        ready      <- channel.messageCount(queue)
        _          <- Console.printLine(s"Ready messages with prefetch 5 and no acks: $ready")
        _          <- fiber.interrupt
        _          <- channel.queueDelete(queue)
      } yield ()
    }
}
