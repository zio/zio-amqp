package nl.vroste.zio.amqp

import com.rabbitmq.client.{ Channel => RChannel, _ }
import nl.vroste.zio.amqp.model.{ ConsumerTag, DeliveryTag, ExchangeName, ExchangeType, QueueName, RoutingKey }
import zio.ZIO.attemptBlocking
import zio._
import zio.stream.ZStream

import java.net.URI
import scala.jdk.CollectionConverters._

/**
 * Thread-safe access to a RabbitMQ Channel
 */
class Channel private[amqp] (channel: RChannel, access: Semaphore) {

  /**
   * Declare a queue
   * @param queue
   *   Name of the queue. If left empty, a random queue name is used
   * @param durable
   *   True if we are declaring a durable queue (the queue will survive a server restart)
   * @param exclusive
   *   Exclusive to this connection
   * @param autoDelete
   *   True if we are declaring an autodelete queue (server will delete it when no longer in use)
   * @param arguments
   * @return
   *   The name of the created queue
   */
  def queueDeclare(
    queue: QueueName,
    durable: Boolean = false,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Any, Throwable, String] = withChannelBlocking(
    _.queueDeclare(
      QueueName.unwrap(queue),
      durable,
      exclusive,
      autoDelete,
      arguments.asJava
    )
  ).map(_.getQueue)

  /**
   * Delete a queue
   *
   * @param queue
   *   Name of the queue
   * @param ifUnused
   *   True if the queue should be deleted only if not in use
   * @param ifEmpty
   *   True if the queue should be deleted only if empty
   */
  def queueDelete(
    queue: QueueName,
    ifUnused: Boolean = false,
    ifEmpty: Boolean = false
  ): ZIO[Any, Throwable, Unit] = withChannelBlocking(
    _.queueDelete(
      QueueName.unwrap(queue),
      ifUnused,
      ifEmpty
    )
  ).unit

  def exchangeDeclare(
    exchange: ExchangeName,
    `type`: ExchangeType,
    durable: Boolean = false,
    autoDelete: Boolean = false,
    internal: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Any, Throwable, Unit] = withChannelBlocking(
    _.exchangeDeclare(
      ExchangeName.unwrap(exchange),
      `type`,
      durable,
      autoDelete,
      internal,
      arguments.asJava
    )
  ).unit

  def exchangeDelete(
    exchange: ExchangeName,
    ifUnused: Boolean = false
  ): ZIO[Any, Throwable, Unit] = withChannelBlocking(
    _.exchangeDelete(
      ExchangeName.unwrap(exchange),
      ifUnused
    )
  ).unit

  def queueBind(
    queue: QueueName,
    exchange: ExchangeName,
    routingKey: RoutingKey,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Any, Throwable, Unit] = withChannelBlocking(
    _.queueBind(
      QueueName.unwrap(queue),
      ExchangeName.unwrap(exchange),
      RoutingKey.unwrap(routingKey),
      arguments.asJava
    )
  ).unit

  def basicQos(
    count: Int,
    global: Boolean = false
  ): ZIO[Any, Throwable, Unit] =
    withChannelBlocking(_.basicQos(count, global)).unit

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
    queue: QueueName,
    consumerTag: ConsumerTag,
    autoAck: Boolean = false
  ): ZStream[Any, Throwable, Delivery] =
    ZStream
      .asyncZIO[Any, Throwable, Delivery] { offer =>
        withChannel { c =>
          attemptBlocking {
            c.basicConsume(
              QueueName.unwrap(queue),
              autoAck,
              ConsumerTag.unwrap(consumerTag),
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
        withChannel(c =>
          attemptBlocking(
            c.basicCancel(ConsumerTag.unwrap(consumerTag))
          )
        ).ignore
      }

  def ack(deliveryTag: DeliveryTag, multiple: Boolean = false): ZIO[Any, Throwable, Unit] =
    withChannel(c =>
      attemptBlocking(
        c.basicAck(deliveryTag, multiple)
      )
    )

  def ackMany(deliveryTags: Seq[DeliveryTag]): ZIO[Any, Throwable, Unit] =
    ack(deliveryTags.max[Long], multiple = true)

  def nack(
    deliveryTag: DeliveryTag,
    requeue: Boolean = false,
    multiple: Boolean = false
  ): ZIO[Any, Throwable, Unit] =
    withChannel(c =>
      attemptBlocking(
        c.basicNack(deliveryTag, multiple, requeue)
      )
    )

  def nackMany(deliveryTags: Seq[DeliveryTag], requeue: Boolean = false): ZIO[Any, Throwable, Unit] =
    nack(deliveryTags.max[Long], requeue, multiple = true)

  def publish(
    exchange: ExchangeName,
    body: Array[Byte],
    routingKey: RoutingKey = RoutingKey(""),
    mandatory: Boolean = false,
    immediate: Boolean = false,
    props: AMQP.BasicProperties = new AMQP.BasicProperties()
  ): ZIO[Any, Throwable, Unit] =
    withChannel(c =>
      attemptBlocking(
        c.basicPublish(
          ExchangeName.unwrap(exchange),
          RoutingKey.unwrap(routingKey),
          mandatory,
          immediate,
          props,
          body
        )
      )
    )

  private[amqp] def withChannel[R, T](f: RChannel => ZIO[R, Throwable, T]) =
    access.withPermit(f(channel))

  private[amqp] def withChannelBlocking[R, T](f: RChannel => T) =
    access.withPermit(attemptBlocking(f(channel)))
}

object Amqp {

  /**
   * Creates a Connection
   *
   * @param factory
   *   Connection factory
   * @return
   *   Connection as a managed resource
   */
  def connect(factory: ConnectionFactory): ZManaged[Any, Throwable, Connection] =
    attemptBlocking(factory.newConnection()).toManagedWith(c => UIO(c.close()))

  def connect(uri: URI): ZManaged[Any, Throwable, Connection] = {
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
  def createChannel(connection: Connection): ZManaged[Any, Throwable, Channel] =
    (for {
      channel <- Task(connection.createChannel())
      permit  <- Semaphore.make(1)
    } yield new Channel(channel, permit)).toManagedWith(_.withChannel(c => attemptBlocking(c.close())).orDie)

}
