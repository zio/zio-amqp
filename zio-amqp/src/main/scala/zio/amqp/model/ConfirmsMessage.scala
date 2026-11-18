package zio.amqp.model

sealed trait ConfirmsMessage extends Product with Serializable {
  val deliveryTag: Long
  val content: Array[Byte]
}

object ConfirmsMessage {
  final case class Ack(override val deliveryTag: Long, override val content: Array[Byte])  extends ConfirmsMessage
  final case class Nack(override val deliveryTag: Long, override val content: Array[Byte]) extends ConfirmsMessage
}
