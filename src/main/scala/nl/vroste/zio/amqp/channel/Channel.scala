package nl.vroste.zio.amqp.channel

import com.rabbitmq.client.AMQP.Exchange.{ DeclareOk => EDeclareOk, DeleteOk => EDeleteOk }
import com.rabbitmq.client.AMQP.Queue.{ BindOk => QBindOk, DeclareOk => QDeclareOk, DeleteOk => QDeleteOk }
import com.rabbitmq.client.{ Channel => RChannel, _ }
import zio.ZIO.attemptBlocking
import zio.stream.ZStream
import zio.{ Chunk, Semaphore, Task, ZIO, ZManaged }

import nl.vroste.zio.amqp.connection.Connection
import nl.vroste.zio.amqp.model._

import scala.jdk.CollectionConverters._

/**
 * Thread-safe access to a RabbitMQ Channel
 */
class Channel private[amqp] (rchannel: RChannel, access: Semaphore) {

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
   *   A declaration-confirm method to indicate the queue was successfully declared
   */
  def queueDeclare(
    queue: QueueName,
    durable: Boolean = false,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
  ): Task[QDeclareOk] = withChannelBlocking(
    _.queueDeclare(
      QueueName.unwrap(queue),
      durable,
      exclusive,
      autoDelete,
      arguments.asJava
    )
  )

  /**
   * Check if a queue exists
   * @param queue
   *   Name of the queue.
   * @return
   *   A declaration-confirm method to indicate the queue exists
   */
  def queueDeclarePassive(
    queue: QueueName
  ): Task[QDeclareOk] = withChannelBlocking(
    _.queueDeclarePassive(
      QueueName.unwrap(queue)
    )
  )

  /**
   * Delete a queue
   *
   * @param queue
   *   Name of the queue
   * @param ifUnused
   *   True if the queue should be deleted only if not in use
   * @param ifEmpty
   *   True if the queue should be deleted only if empty
   * @return
   *   A deletion-confirm method to indicate the queue was successfully deleted
   */
  def queueDelete(
    queue: QueueName,
    ifUnused: Boolean = false,
    ifEmpty: Boolean = false
  ): Task[QDeleteOk] = withChannelBlocking(
    _.queueDelete(
      QueueName.unwrap(queue),
      ifUnused,
      ifEmpty
    )
  )

  def exchangeDeclare(
    exchange: ExchangeName,
    `type`: ExchangeType,
    durable: Boolean = false,
    autoDelete: Boolean = false,
    internal: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
  ): Task[EDeclareOk] = withChannelBlocking(
    _.exchangeDeclare(
      ExchangeName.unwrap(exchange),
      `type`,
      durable,
      autoDelete,
      internal,
      arguments.asJava
    )
  )

  def exchangeDelete(
    exchange: ExchangeName,
    ifUnused: Boolean = false
  ): Task[EDeleteOk] = withChannelBlocking(
    _.exchangeDelete(
      ExchangeName.unwrap(exchange),
      ifUnused
    )
  )

  def queueBind(
    queue: QueueName,
    exchange: ExchangeName,
    routingKey: RoutingKey,
    arguments: Map[String, AnyRef] = Map.empty
  ): Task[QBindOk] = withChannelBlocking(
    _.queueBind(
      QueueName.unwrap(queue),
      ExchangeName.unwrap(exchange),
      RoutingKey.unwrap(routingKey),
      arguments.asJava
    )
  )

  def basicQos(
    count: Int,
    global: Boolean = false
  ): Task[Unit] = withChannelBlocking(
    _.basicQos(count, global)
  )

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
  ): ZStream[Any, Throwable, Delivery] = ZStream
    .asyncZIO[Any, Throwable, Delivery](offer =>
      withChannelBlocking(
        _.basicConsume(
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
      )
    )
    .ensuring {
      withChannelBlocking(
        _.basicCancel(ConsumerTag.unwrap(consumerTag))
      ).orDie
    }

  def ackMany(deliveryTags: Seq[DeliveryTag]): Task[Unit] =
    ack(deliveryTags.max[Long], multiple = true)

  def ack(
    deliveryTag: DeliveryTag,
    multiple: Boolean = false
  ): Task[Unit] =
    withChannelBlocking(
      _.basicAck(deliveryTag, multiple)
    )

  def nackMany(
    deliveryTags: Seq[DeliveryTag],
    requeue: Boolean = false
  ): Task[Unit] =
    nack(deliveryTags.max[Long], requeue, multiple = true)

  def nack(
    deliveryTag: DeliveryTag,
    requeue: Boolean = false,
    multiple: Boolean = false
  ): Task[Unit] =
    withChannelBlocking(
      _.basicNack(deliveryTag, multiple, requeue)
    )

  def publish(
    exchange: ExchangeName,
    body: Array[Byte],
    routingKey: RoutingKey = RoutingKey.default,
    mandatory: Boolean = false,
    immediate: Boolean = false,
    props: AMQP.BasicProperties = new AMQP.BasicProperties()
  ): Task[Unit] =
    withChannelBlocking(
      _.basicPublish(
        ExchangeName.unwrap(exchange),
        RoutingKey.unwrap(routingKey),
        mandatory,
        immediate,
        props,
        body
      )
    )

  /**
   * Returns the number of messages in a queue ready to be delivered to consumers. This method assumes the queue exists.
   * If it doesn't, the channels will be closed with an exception.
   *
   * @param queue
   *   the name of the queue
   * @return
   *   the number of messages in ready state
   */
  def messageCount(queue: QueueName): Task[Long] =
    withChannelBlocking(
      _.messageCount(QueueName.unwrap(queue))
    )

  /**
   * Returns the number of consumers on a queue. This method assumes the queue exists. If it doesn't, the channel will
   * be closed with an exception.
   *
   * @param queue
   *   the name of the queue
   * @return
   *   the number of consumers
   */
  def consumerCount(queue: QueueName): Task[Long] =
    withChannelBlocking(
      _.consumerCount(QueueName.unwrap(queue))
    )

  private[amqp] def withChannelBlocking[T](f: RChannel => T): Task[T] =
    access.withPermit(attemptBlocking(f(rchannel)))
}

object Channel {
  private[amqp] def make(
    connection: Connection
  ): ZManaged[Any, Throwable, Channel] = ZManaged.acquireReleaseWith(
    for {
      sema  <- Semaphore.make(1)
      rchan <- connection
                 .withConnectionBlocking(rconn =>
                   Option(
                     rconn.createChannel()
                   ).toRight(
                     new RuntimeException("No channels available")
                   )
                 )
                 .absolve
    } yield new Channel(rchan, sema)
  )(
    _.withChannelBlocking(
      _.close()
    ).orDie
  )
}
