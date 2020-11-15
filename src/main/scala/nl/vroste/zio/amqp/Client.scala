package nl.vroste.zio.amqp

import java.net.URI

import com.rabbitmq.client.impl.nio.NioParams
import com.rabbitmq.client.{ Channel => RChannel, _ }
import zio._
import zio.blocking.{ effectBlocking, Blocking }
import zio.stream.ZStream

import scala.jdk.CollectionConverters._

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

/**
 * Thread-safe access to a RabbitMQ Channel
 */
class Channel private[amqp] (channel: RChannel, access: Semaphore) {
  def queueDeclare(
    queue: String,
    durable: Boolean = false,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Blocking, Throwable, Unit] = withChannelBlocking(
    _.queueDeclare(queue, durable, exclusive, autoDelete, arguments.asJava)
  ).unit

  def exchangeDeclare(
    exchange: String,
    `type`: ExchangeType,
    durable: Boolean = false,
    autoDelete: Boolean = false,
    internal: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Blocking, Throwable, Unit] = withChannelBlocking(
    _.exchangeDeclare(exchange, ExchangeType.toRabbitMqType(`type`), durable, autoDelete, internal, arguments.asJava)
  ).unit

  def queueBind(
    queue: String,
    exchange: String,
    routingKey: String,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Blocking, Throwable, Unit] =
    withChannelBlocking(_.queueBind(queue, exchange, routingKey, arguments.asJava)).unit

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
    props: AMQP.BasicProperties = new AMQP.BasicProperties()
  ): ZIO[Blocking, Throwable, Unit] =
    withChannel(c => effectBlocking(c.basicPublish(exchange, routingKey, mandatory, immediate, props, body)))

  private[amqp] def withChannel[R, T](f: RChannel => ZIO[R, Throwable, T]) =
    access.withPermit(f(channel))

  private[amqp] def withChannelBlocking[R, T](f: RChannel => T) =
    access.withPermit(effectBlocking(f(channel)))
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
