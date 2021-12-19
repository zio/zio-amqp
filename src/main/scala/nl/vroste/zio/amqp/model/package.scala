package nl.vroste.zio.amqp

import zio.prelude.{ Newtype, Subtype }

import java.net.URI

package object model {
  object QueueName extends Newtype[String]
  type QueueName = QueueName.Type

  object ExchangeName extends Newtype[String]
  type ExchangeName = ExchangeName.Type

  object RoutingKey extends Newtype[String]
  type RoutingKey = RoutingKey.Type

  object ConsumerTag extends Newtype[String]
  type ConsumerTag = ConsumerTag.Type

  // DeliveryTag is subtype of Long
  // because we need ordering
  object DeliveryTag extends Subtype[Long]
  type DeliveryTag = DeliveryTag.Type

  object AmqpUri extends Subtype[URI]
  type AmqpUri = AmqpUri.Type
}
