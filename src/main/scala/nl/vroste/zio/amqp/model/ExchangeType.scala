package nl.vroste.zio.amqp.model

import enumeratum.{ Enum, EnumEntry }
import enumeratum.EnumEntry.Snakecase

sealed trait ExchangeType extends EnumEntry with Snakecase
object ExchangeType       extends Enum[ExchangeType] {
  override def values: IndexedSeq[ExchangeType] = findValues

  case object Direct  extends ExchangeType
  case object Fanout  extends ExchangeType
  case object Topic   extends ExchangeType
  case object Headers extends ExchangeType
}
