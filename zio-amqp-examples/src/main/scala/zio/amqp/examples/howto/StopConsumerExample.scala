//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.0
// Run from this folder:  scala-cli run StopConsumerExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.StopConsumerExample"

package zio.amqp.examples.howto

import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Stop a Consumer
 *
 * Stops a consumer gracefully with ZStream#haltWhen: the task being processed finishes, no new task starts, and the
 * consumer is cancelled on the broker.
 */
object StopConsumerExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("howto-stop")

  def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.queueDeclare(queue)
        _          <- channel.purgeQueue(queue)
        _          <- ZIO.foreachDiscard(1 to 3) { i =>
                        channel.publish(ExchangeName(""), s"task-$i".getBytes("UTF-8"), RoutingKey(QueueName.unwrap(queue)))
                      }
        shutdown   <- Promise.make[Throwable, Unit]
        _          <- channel.basicQos(1)
        fiber      <- channel
                        .consume(queue, ConsumerTag("stop-example"))
                        .haltWhen(shutdown)
                        .mapZIO { delivery =>
                          val task = new String(delivery.getBody, "UTF-8")
                          Console.printLine(s"started $task") *>
                            ZIO.sleep(1.second) *>
                            channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag)) *>
                            Console.printLine(s"finished $task")
                        }
                        .runDrain
                        .fork
        _          <- ZIO.sleep(500.millis)
        _          <- Console.printLine("shutdown requested")
        _          <- shutdown.succeed(())
        _          <- fiber.join
        _          <- ZIO.sleep(500.millis)
        consumers  <- channel.consumerCount(queue)
        left       <- channel.messageCount(queue)
        _          <- Console.printLine(s"consumers: $consumers, messages left: $left")
        _          <- channel.queueDelete(queue)
      } yield ()
    }
}
