//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.1
// Run from this folder:  scala-cli run AcknowledgeExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.AcknowledgeExample"

package zio.amqp.examples.howto

import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Acknowledge Messages
 *
 * Acknowledges a good message, rejects a failing one with requeue, and prints the redelivered flag.
 */
object AcknowledgeExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("howto-ack")

  def publish(channel: Channel, body: String): ZIO[Any, Throwable, Unit] =
    channel.publish(ExchangeName(""), body.getBytes("UTF-8"), RoutingKey(QueueName.unwrap(queue)))

  def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.queueDeclare(queue)
        _          <- channel.purgeQueue(queue)
        _          <- publish(channel, "good")
        _          <- publish(channel, "flaky")
        failedOnce <- Ref.make(false)
        _          <- channel
                        .consume(queue, ConsumerTag("ack-example"))
                        .mapZIO { delivery =>
                          val tag    = DeliveryTag(delivery.getEnvelope.getDeliveryTag)
                          val body   = new String(delivery.getBody, "UTF-8")
                          val redone = delivery.getEnvelope.isRedeliver
                          val handle =
                            if (body == "flaky")
                              failedOnce.getAndSet(true).flatMap { alreadyFailed =>
                                if (alreadyFailed) channel.ack(tag).as("acked")
                                else channel.nack(tag, requeue = true).as("nacked and requeued")
                              }
                            else channel.ack(tag).as("acked")
                          handle.flatMap(result => Console.printLine(s"$body (redelivered = $redone): $result"))
                        }
                        .take(3)
                        .runDrain
        _          <- channel.queueDelete(queue)
      } yield ()
    }
}
