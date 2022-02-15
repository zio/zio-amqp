package nl.vroste.zio.amqp.connection

import com.rabbitmq.client.{ Connection => RConnection, ConnectionFactory }
import zio.ZIO.attemptBlocking
import zio.{ Task, ZManaged }

import nl.vroste.zio.amqp.channel.Channel

import java.net.URI

final class Connection private[amqp] (rconnection: RConnection) {
  def createChannel: ZManaged[Any, Throwable, Channel] =
    Channel.make(this)

  private[amqp] def withConnectionBlocking[T](f: RConnection => T): Task[T] =
    attemptBlocking(f(rconnection))
}

object Connection {

  def connect(uri: URI): ZManaged[Any, Throwable, Connection] = {
    val factory = new ConnectionFactory()
    factory.setUri(uri)
    make(factory)
  }

  def connect(factory: ConnectionFactory): ZManaged[Any, Throwable, Connection] =
    make(factory)

  private[amqp] def make(factory: ConnectionFactory): ZManaged[Any, Throwable, Connection] =
    ZManaged.acquireReleaseWith(
      attemptBlocking(
        factory.newConnection()
      ).map(new Connection(_))
    )(
      _.withConnectionBlocking(
        _.close()
      ).orDie
    )

}
