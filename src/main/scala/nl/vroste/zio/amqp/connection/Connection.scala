package nl.vroste.zio.amqp.connection

import com.rabbitmq.client.{ Connection => RConnection, ConnectionFactory }
import zio.ZIO.attemptBlocking
import zio.{ RIO, Task, UIO, ZManaged }

import nl.vroste.zio.amqp.channel.Channel

import java.net.URI

final class Connection private[amqp] (rconnection: RConnection) {
  def createChannel: ZManaged[Any, Throwable, Channel] = Channel.make(rconnection)
}

object Connection {

  def connect(uri: URI): ZManaged[Any, Throwable, Connection] = {
    val factory = new ConnectionFactory()
    factory.setUri(uri)
    make(factory)
  }

  def connect(factory: ConnectionFactory): ZManaged[Any, Throwable, Connection] =
    make(factory)

  private[amqp] def make(factory: ConnectionFactory): ZManaged[Any, Throwable, Connection] = for {
    rconn <- ZManaged.acquireReleaseWith(acquire(factory))(release)
  } yield new Connection(rconn)

  private def acquire(factory: ConnectionFactory): RIO[Any, RConnection] =
    attemptBlocking(factory.newConnection())

  private def release: RConnection => UIO[Unit] =
    rconn => Task(rconn.close()).orDie

}
