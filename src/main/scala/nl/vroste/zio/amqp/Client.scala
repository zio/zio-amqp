package nl.vroste.zio.amqp

import java.net.URI

import com.rabbitmq.client.{ Channel => RChannel, _ }
import zio._
import zio.blocking.{ effectBlocking, Blocking }
import zio.stream.ZStream

/**
 * Thread-safe access to a RabbitMQ Channel
 */
class Channel private[amqp] (channel: RChannel, access: Semaphore) {

  /**
   * Consume a stream of messages from a queue
   *
   * When the stream is completed, the AMQP consumption is cancelled
   *
   * @param queue
   * @param consumerTag
   * @param autoAck
   * @return
   */
  def consume(
    queue: String,
    consumerTag: String,
    autoAck: Boolean = false
  ): ZStream[Blocking, Throwable, Delivery] =
    ZStream
      .effectAsyncM[Blocking, Throwable, Delivery] { offer =>
        withChannel { c =>
          effectBlocking {
            c.basicConsume(
              queue,
              autoAck,
              consumerTag,
              (_: String, message: Delivery) => offer(ZIO.succeed(message)),
              (_: String) => offer(ZIO.fail(None)),
              (_: String, sig: ShutdownSignalException) => offer(ZIO.fail(Some(sig)))
            )
          }
        }
      }
      .ensuring {
        withChannel(c => effectBlocking(c.basicCancel(consumerTag))).ignore
      }

  def ack(deliveryTag: Long, multiple: Boolean = false): ZIO[Blocking, Throwable, Unit] =
    withChannel { c =>
      effectBlocking(c.basicAck(deliveryTag, multiple))
    }

  def ackMany(deliveryTag: Seq[Long]): ZIO[Blocking, Throwable, Unit] =
    ack(deliveryTag.max, multiple = true)

  def nack(
    deliveryTag: Long,
    requeue: Boolean = false,
    multiple: Boolean = false
  ): ZIO[Blocking, Throwable, Unit] =
    withChannel { c =>
      effectBlocking(c.basicNack(deliveryTag, multiple, requeue))
    }

  def nackMany(deliveryTag: Seq[Long], requeue: Boolean = false): ZIO[Blocking, Throwable, Unit] =
    nack(deliveryTag.max, requeue, multiple = true)

  private[amqp] def withChannel[R, T](f: RChannel => ZIO[R, Throwable, T]) =
    access.withPermit(f(channel))
}

object Amqp {

  /**
   * Creates a Connection that makes use of the ZIO Platform's executor service
   *
   * @param factory Connection factory. NIO is enabled by calling this method.
   * @return Connection as a managed resource
   */
  def connect(factory: ConnectionFactory): ZManaged[Blocking, Throwable, Connection] =
    ZIO
      .runtime[Any]
      .flatMap { runtime =>
        effectBlocking(factory.newConnection(runtime.Platform.executor.asECES))
      }
      .toManaged(c => UIO(c.close()))

  def connect(uri: URI): ZManaged[Blocking, Throwable, Connection] = {
    val factory = new ConnectionFactory()
    factory.setUri(uri)
    connect(factory)
  }

  /**
   * Creates a Channel that is safe for concurrent access
   *
   * @param connection
   * @return
   */
  def createChannel(connection: Connection): ZManaged[Blocking, Throwable, Channel] =
    (for {
      channel <- Task(connection.createChannel())
      permit  <- Semaphore.make(1)
    } yield new Channel(channel, permit)).toManaged(_.withChannel(c => effectBlocking(c.close())).orDie)

}
