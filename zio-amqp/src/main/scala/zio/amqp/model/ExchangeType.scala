package zio.amqp.model

import scala.language.implicitConversions

/**
 * The type of an exchange. The type decides how the exchange routes messages to the queues bound to it.
 *
 * {{{
 * val direct: ExchangeType = ExchangeType.Direct
 * val delayed: ExchangeType = ExchangeType.Custom("x-delayed-message")
 * }}}
 */
sealed trait ExchangeType extends Product with Serializable {

  /**
   * The name that the broker uses for this type, such as `direct`.
   */
  def name: String
}

/**
 * The exchange types, and the conversion of an `ExchangeType` to its name.
 */
object ExchangeType {

  /**
   * Routes a message to the queues that are bound with the same routing key. Its name is `direct`.
   */
  case object Direct extends ExchangeType {
    override val name: String = "direct"
  }

  /**
   * Routes a message to every queue bound to the exchange. The routing key is ignored. Its name is `fanout`.
   */
  case object Fanout extends ExchangeType {
    override val name: String = "fanout"
  }

  /**
   * Routes a message to the queues whose binding pattern matches the routing key. In a pattern, `*` matches one word
   * and `#` matches zero or more words, where the words of a routing key are separated by dots. Its name is `topic`.
   */
  case object Topic extends ExchangeType {
    override val name: String = "topic"
  }

  /**
   * Routes a message by the headers of the message instead of its routing key. Its name is `headers`.
   */
  case object Headers extends ExchangeType {
    override val name: String = "headers"
  }

  /**
   * An exchange type that the broker knows by its own name, such as a type that a plugin adds.
   *
   * @param type
   *   The name of the type, which is passed to the broker as it is
   */
  case class Custom(`type`: String) extends ExchangeType {
    override val name: String = `type`
  }

  /**
   * Converts an exchange type to its name, so an `ExchangeType` can be passed where a `String` is expected. The result
   * is `name`.
   */
  implicit def represent(`type`: ExchangeType): String = `type`.name
}
