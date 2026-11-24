package zio.amqp

import zio.prelude.{ Newtype, Subtype }

import java.net.URI

/**
 * The names and values that the AMQP client API uses: queue, exchange, routing key and consumer names, delivery tags
 * and URIs.
 */
package object model {

  /**
   * The name of a queue. It is a `Newtype` over `String`, so a queue name can't be passed where an exchange name or a
   * routing key is expected.
   */
  object QueueName extends Newtype[String]

  /**
   * The type of a queue name, see `QueueName`.
   */
  type QueueName = QueueName.Type

  /**
   * The name of an exchange. It is a `Newtype` over `String`, so an exchange name can't be passed where a queue name is
   * expected. The empty name `ExchangeName("")` is the default exchange.
   */
  object ExchangeName extends Newtype[String]

  /**
   * The type of an exchange name, see `ExchangeName`.
   */
  type ExchangeName = ExchangeName.Type

  /**
   * The routing key of a message, or the binding key of a queue binding. Direct and topic exchanges, and the default
   * exchange, use it to decide which queues receive the message. Fanout and headers exchanges ignore it.
   */
  object RoutingKey extends Newtype[String]

  /**
   * The type of a routing key, see `RoutingKey`.
   */
  type RoutingKey = RoutingKey.Type

  /**
   * The tag that identifies a consumer on its channel. `Channel.consume` uses it to cancel the consumer.
   */
  object ConsumerTag extends Newtype[String]

  /**
   * The type of a consumer tag, see `ConsumerTag`.
   */
  type ConsumerTag = ConsumerTag.Type

  // DeliveryTag is subtype of Long
  // because we need ordering
  /**
   * The number that the broker gives a delivery on a channel. It is a `Subtype` of `Long`, so a delivery tag can be
   * used where a `Long` is expected. `Channel.ackMany` and `Channel.nackMany` use the ordering of `Long` to find the
   * largest tag.
   */
  object DeliveryTag extends Subtype[Long]

  /**
   * The type of a delivery tag, see `DeliveryTag`.
   */
  type DeliveryTag = DeliveryTag.Type

  /**
   * The AMQP URI of a connection, as built by `AMQPConfig.toUri`. It is a `Subtype` of `URI`.
   */
  object AmqpUri extends Subtype[URI]

  /**
   * The type of an AMQP URI, see `AmqpUri`.
   */
  type AmqpUri = AmqpUri.Type
}
