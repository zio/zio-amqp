package zio.amqp

import com.rabbitmq.client.{ Connection, ConnectionFactory }
import zio._
import zio.amqp.model.{ AMQPConfig, ConfirmsMessage }

import java.net.URI
import scala.collection.immutable.TreeMap

object Amqp {

  /**
   * Creates a Connection
   *
   * @param factory
   *   Connection factory
   * @return
   *   Connection as a managed resource
   */
  def connect(factory: ConnectionFactory): ZIO[Scope, Throwable, Connection] =
    ZIO.acquireRelease(ZIO.attemptBlocking(factory.newConnection()))(c => ZIO.attempt(c.close()).orDie)

  def connect(uri: URI): ZIO[Scope, Throwable, Connection]               = {
    val factory = new ConnectionFactory()
    factory.setUri(uri)
    connect(factory)
  }
  def connect(amqpConfig: AMQPConfig): ZIO[Scope, Throwable, Connection] = {
    val factory = new ConnectionFactory()
    factory.setUri(amqpConfig.toUri)
    connect(factory)
  }

  /**
   * Creates a Channel that is safe for concurrent access
   *
   * @param connection
   * @return
   */

  def createChannel(connection: Connection): ZIO[Scope, Throwable, Channel] =
    (for {
      channel <- ZIO.attempt(connection.createChannel())
      permit  <- Semaphore.make(1)
    } yield new Channel(channel, permit)).withFinalizer(_.withChannel(c => ZIO.attemptBlocking(c.close())).orDie)

  def createChannelConfirms(
    connection: Connection,
    queue: Queue[ConfirmsMessage]
  ): ZIO[Scope, Throwable, ConfirmsChannel] =
    (for {
      channel <- ZIO.attempt(connection.createChannel())
      _       <- ZIO.logDebug("Enabling confirms select")
      _       <- ZIO.attempt(channel.confirmSelect())
      map     <- Ref.make(TreeMap.empty[Long, ConfirmsChannel.PendingConfirmationMessage])
      _       <- ZIO.attempt(channel.addConfirmListener(ConfirmsChannel.listener(queue, map)))
      permit  <- Semaphore.make(1)
    } yield new ConfirmsChannel(channel, permit, queue, map)).withFinalizer { c =>
      c.withChannel(c => ZIO.attemptBlocking(c.close())).orDie
    }

  def createChannelConfirmsDefault(connection: Connection): ZIO[Scope, Throwable, ConfirmsChannel] =
    Queue.sliding[ConfirmsMessage](100).flatMap(createChannelConfirms(connection, _))
}
