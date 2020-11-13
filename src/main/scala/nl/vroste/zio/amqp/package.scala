package nl.vroste.zio
import com.rabbitmq.client.BuiltinExchangeType

package object amqp {
  sealed trait ExchangeType
  object ExchangeType {
    case object Direct  extends ExchangeType
    case object Fanout  extends ExchangeType
    case object Topic   extends ExchangeType
    case object Headers extends ExchangeType

    def toRabbitMqType(t: ExchangeType): BuiltinExchangeType = t match {
      case Direct  => BuiltinExchangeType.DIRECT
      case Fanout  => BuiltinExchangeType.FANOUT
      case Topic   => BuiltinExchangeType.TOPIC
      case Headers => BuiltinExchangeType.HEADERS
    }
  }
}
