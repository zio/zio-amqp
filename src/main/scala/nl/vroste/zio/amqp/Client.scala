package nl.vroste.zio.amqp

import java.net.URI

import com.rabbitmq.client.impl.nio.NioParams
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
              new DeliverCallback                {
                override def handle(consumerTag: String, message: Delivery): Unit =
                  offer(ZIO.succeed(Chunk.single(message)))
              },
              new CancelCallback                 {
                override def handle(consumerTag: String): Unit = offer(ZIO.fail(None))
              },
              new ConsumerShutdownSignalCallback {
                override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
                  offer(ZIO.fail(Some(sig)))
              }
            )
          }
        }
      }
      .ensuring {
        withChannel(c => effectBlocking(c.basicCancel(consumerTag))).ignore
      }

  def ack(deliveryTag: Long, multiple: Boolean = false): ZIO[Blocking, Throwable, Unit] =
    withChannel(c => effectBlocking(c.basicAck(deliveryTag, multiple)))

  def ackMany(deliveryTag: Seq[Long]): ZIO[Blocking, Throwable, Unit] =
    ack(deliveryTag.max, multiple = true)

  def nack(
    deliveryTag: Long,
    requeue: Boolean = false,
    multiple: Boolean = false
  ): ZIO[Blocking, Throwable, Unit] =
    withChannel(c => effectBlocking(c.basicNack(deliveryTag, multiple, requeue)))

  def nackMany(deliveryTag: Seq[Long], requeue: Boolean = false): ZIO[Blocking, Throwable, Unit] =
    nack(deliveryTag.max, requeue, multiple = true)

  def publish(
    exchange: String,
    body: Array[Byte],
    routingKey: String = "",
    mandatory: Boolean = false,
    immediate: Boolean = false,
    props: AMQP.BasicProperties
  ) =
    withChannel(c => effectBlocking(c.basicPublish(exchange, routingKey, mandatory, immediate, props, body)))

  private[amqp] def withChannel[R, T](f: RChannel => ZIO[R, Throwable, T]) =
    access.withPermit(f(channel))
}

object Amqp {

  /**
   * Creates a Connection that makes use of the ZIO Platform's executor service
   *
   * @param factory Connection factory
   * @return Connection as a managed resource
   */
  def connect(factory: ConnectionFactory): ZManaged[Blocking, Throwable, Connection] =
    ZIO
      .runtime[Any]
      .flatMap { runtime =>
        val eces = runtime.platform.executor.asECES
        factory.useNio()
        factory.setNioParams(new NioParams().setNioExecutor(eces))
        effectBlocking(factory.newConnection(eces))
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
