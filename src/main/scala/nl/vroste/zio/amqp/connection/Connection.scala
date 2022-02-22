package nl.vroste.zio.amqp.connection

import com.rabbitmq.client.{ Connection => RConnection, ConnectionFactory }
import zio.ZIO.attemptBlocking
import zio.{ Task, ZIO, ZManaged }

import nl.vroste.zio.amqp.channel.Channel
import nl.vroste.zio.amqp.connection.config.ConnectionConfig

final class Connection private[amqp] (rconnection: RConnection) {
  def channelManaged: ZManaged[Any, Throwable, Channel] =
    Channel.make(this)

  private[amqp] def withConnectionBlocking[T](f: RConnection => T): Task[T] =
    attemptBlocking(f(rconnection))
}

object Connection {
  def managed(config: ConnectionConfig): ZManaged[Any, Throwable, Connection] =
    ZIO.attempt {
      config.settings.foldRight(new ConnectionFactory)(_ apply _)
    }.toManaged.flatMap(make)

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
