package nl.vroste.zio.amqp

import io.estatico.newtype.macros.newtype

package object model {
  @newtype case class QueueName(name: String)

  @newtype case class ExchangeName(name: String)

  @newtype case class RoutingKey(key: String)

  @newtype case class ConsumerTag(tag: String)

  @newtype case class DeliveryTag(tag: Long)

  object DeliveryTag {
    implicit val ordering: Ordering[DeliveryTag] = Ordering.by(_.tag)
  }
}
