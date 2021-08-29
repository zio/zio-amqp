package nl.vroste.zio.amqp

import zio.prelude.Subtype

package object model {
  object QueueName extends Subtype[String]
  type QueueName = QueueName.Type

  object ExchangeName extends Subtype[String]
  type ExchangeName = ExchangeName.Type

  object RoutingKey extends Subtype[String]
  type RoutingKey = RoutingKey.Type

  object ConsumerTag extends Subtype[String]
  type ConsumerTag = ConsumerTag.Type

  object DeliveryTag extends Subtype[Long]
  type DeliveryTag = DeliveryTag.Type
}
