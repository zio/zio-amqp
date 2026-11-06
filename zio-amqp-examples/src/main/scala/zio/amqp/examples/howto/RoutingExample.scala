//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.1
// Run from this folder:  scala-cli run RoutingExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.RoutingExample"

package zio.amqp.examples.howto

import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Route Messages with Exchanges
 *
 * Binds two queues to a topic exchange with different patterns and shows which messages each receives.
 */
object RoutingExample extends ZIOAppDefault {

  val exchange: ExchangeName = ExchangeName("howto-events")
  val orders: QueueName      = QueueName("howto-orders")
  val errors: QueueName      = QueueName("howto-errors")

  def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.exchangeDeclare(exchange, ExchangeType.Topic)
        _          <- channel.queueDeclare(orders)
        _          <- channel.queueDeclare(errors)
        _          <- channel.queueBind(orders, exchange, RoutingKey("orders.*"))
        _          <- channel.queueBind(errors, exchange, RoutingKey("*.error"))
        _          <- ZIO.foreachDiscard(List("orders.created", "payments.error", "orders.error")) { key =>
                        channel.publish(exchange, key.getBytes("UTF-8"), RoutingKey(key))
                      }
        _          <- ZIO.sleep(500.millis)
        ordersN    <- channel.messageCount(orders)
        errorsN    <- channel.messageCount(errors)
        _          <- Console.printLine(s"orders.* queue: $ordersN messages, *.error queue: $errorsN messages")
        _          <- channel.queueDelete(orders)
        _          <- channel.queueDelete(errors)
        _          <- channel.exchangeDelete(exchange)
      } yield ()
    }
}
