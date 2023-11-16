package zio.amqp.model

import scala.language.implicitConversions

sealed trait ExchangeType extends Product with Serializable {
  def name: String
}

object ExchangeType {
  case object Direct                extends ExchangeType {
    override val name: String = "direct"
  }
  case object Fanout                extends ExchangeType {
    override val name: String = "fanout"
  }
  case object Topic                 extends ExchangeType {
    override val name: String = "topic"
  }
  case object Headers               extends ExchangeType {
    override val name: String = "headers"
  }
  case class Custom(`type`: String) extends ExchangeType {
    override val name: String = `type`
  }

  implicit def represent(`type`: ExchangeType): String = `type`.name
}
