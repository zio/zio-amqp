//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.2
// Run from this folder:  scala-cli run DurableMessagesExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.DurableMessagesExample"

package zio.amqp.examples.howto

import com.rabbitmq.client.AMQP
import zio._
import zio.amqp._
import zio.amqp.model._

import java.util.Collections

/**
 * Publish Durable Messages
 *
 * Publishes a persistent message with a content type, a correlation id and a header to a durable queue, and reads the
 * properties back on the consumer side.
 */
object DurableMessagesExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("howto-durable")

  def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.queueDeclare(queue, durable = true)
        _          <- channel.purgeQueue(queue)
        props       = new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .deliveryMode(2)
                        .correlationId("order-42")
                        .headers(Collections.singletonMap[String, AnyRef]("source", "howto"))
                        .build()
        _          <- channel.publish(
                        ExchangeName(""),
                        """{"id":42}""".getBytes("UTF-8"),
                        RoutingKey(QueueName.unwrap(queue)),
                        props = props
                      )
        _          <- channel
                        .consume(queue, ConsumerTag("durable-example"))
                        .mapZIO { delivery =>
                          val p = delivery.getProperties
                          Console.printLine(
                            s"deliveryMode=${p.getDeliveryMode} contentType=${p.getContentType} " +
                              s"correlationId=${p.getCorrelationId} source=${p.getHeaders.get("source")}"
                          ) *> channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
                        }
                        .take(1)
                        .runDrain
        _          <- channel.queueDelete(queue)
      } yield ()
    }
}
