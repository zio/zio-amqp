package zio.amqp

import com.rabbitmq.client.AMQP.Queue.{ DeclareOk, PurgeOk }
import com.rabbitmq.client.{ Channel => RChannel, _ }
import zio.ZIO.attemptBlocking
import zio._
import zio.amqp.model._
import zio.stream.ZStream

import java.net.URI
import scala.jdk.CollectionConverters._

/**
 * Thread-safe access to a RabbitMQ Channel.
 *
 * Each method call holds the channel's permit while it calls the RabbitMQ client, so two calls on the same `Channel`
 * run one after the other. Create a channel with [[Amqp.createChannel]]. The channel is closed when its `Scope` ends.
 *
 * {{{
 * import zio._
 * import zio.amqp.model._
 *
 * val program: ZIO[Scope, Throwable, Unit] =
 *   for {
 *     connection <- Amqp.connect(AMQPConfig.default)
 *     channel    <- Amqp.createChannel(connection)
 *     _          <- channel.queueDeclare(QueueName("orders"))
 *     _          <- channel.publish(ExchangeName(""), "order-1".getBytes, RoutingKey("orders"))
 *   } yield ()
 * }}}
 */
class Channel private[amqp] (channel: RChannel, access: Semaphore) {

  /**
   * Declares a queue and returns its name. An empty `queue` name asks the broker to generate one, and the generated
   * name starts with `amq.gen-`.
   *
   * {{{
   * import zio._
   * import zio.amqp.model._
   *
   * def declareOrders(channel: Channel): ZIO[Any, Throwable, String] =
   *   channel.queueDeclare(QueueName("orders"), durable = true)
   * }}}
   *
   * @param queue
   *   Name of the queue. If left empty, the broker generates a name
   * @param durable
   *   True if we are declaring a durable queue (the queue will survive a server restart)
   * @param exclusive
   *   True if the queue is exclusive to this connection
   * @param autoDelete
   *   True if we are declaring an autodelete queue (server will delete it when no longer in use)
   * @param arguments
   *   Extra declaration arguments, passed to the broker as they are, such as `x-dead-letter-exchange`
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
   * Checks that a queue exists, without declaring it. If the queue doesn't exist, the broker closes the channel with a
   * `NOT_FOUND` error (reply code 404), and the channel can't be used afterwards.
   *
   * @param queue
   *   Name of the queue.
   * @return
   *   a declaration-confirm method to indicate the queue exists. It holds the queue name, its message count and its
   *   consumer count
   */
  def queueDeclarePassive(
    queue: QueueName
  ): ZIO[Any, Throwable, DeclareOk] = withChannelBlocking(
    _.queueDeclarePassive(
      QueueName.unwrap(queue)
    )
  )

  /**
   * Deletes a queue. With `ifEmpty = true`, the broker refuses to delete a queue that holds messages, and it closes the
   * channel with a `PRECONDITION_FAILED` error (reply code 406). With `ifUnused = true`, the broker refuses to delete a
   * queue that has consumers, with the same error.
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

  /**
   * Declares an exchange. The `type` is passed to the broker by its name, see `ExchangeType`.
   *
   * An internal exchange can't be published to by clients. A `publish` to an internal exchange closes the channel with
   * an `ACCESS_REFUSED` error (reply code 403).
   *
   * {{{
   * import zio._
   * import zio.amqp.model._
   *
   * def declareOrders(channel: Channel): ZIO[Any, Throwable, Unit] =
   *   channel.exchangeDeclare(ExchangeName("orders"), ExchangeType.Topic, durable = true)
   * }}}
   *
   * @param exchange
   *   Name of the exchange
   * @param type
   *   Type of the exchange, such as `ExchangeType.Topic`
   * @param durable
   *   True if the exchange survives a server restart
   * @param autoDelete
   *   True if the broker deletes the exchange once no queue is bound to it any more
   * @param internal
   *   True if clients can't publish to the exchange directly
   * @param arguments
   *   Extra declaration arguments, passed to the broker as they are
   */
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

  /**
   * Deletes an exchange. With `ifUnused = true`, the broker refuses to delete an exchange that still has bindings, and
   * it closes the channel with a `PRECONDITION_FAILED` error (reply code 406).
   *
   * @param exchange
   *   Name of the exchange
   * @param ifUnused
   *   True if the exchange should be deleted only if it has no bindings
   */
  def exchangeDelete(
    exchange: ExchangeName,
    ifUnused: Boolean = false
  ): ZIO[Any, Throwable, Unit] = withChannelBlocking(
    _.exchangeDelete(
      ExchangeName.unwrap(exchange),
      ifUnused
    )
  ).unit

  /**
   * Binds a queue to an exchange, so the exchange can route messages to the queue. Which messages reach the queue
   * depends on the exchange type: direct and topic exchanges match the `routingKey`, and headers exchanges match the
   * `arguments` against the message headers. Fanout exchanges ignore the routing key.
   *
   * {{{
   * import zio._
   * import zio.amqp.model._
   *
   * def bindEuropeanOrders(channel: Channel): ZIO[Any, Throwable, Unit] =
   *   channel.queueBind(QueueName("orders-eu"), ExchangeName("orders"), RoutingKey("order.eu.*"))
   * }}}
   *
   * @param queue
   *   Name of the queue to bind
   * @param exchange
   *   Name of the exchange to bind to
   * @param routingKey
   *   Routing key of the binding
   * @param arguments
   *   Binding arguments, used by headers exchanges
   */
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

  /**
   * Sets the prefetch count: the number of unacknowledged deliveries that the broker sends before it waits for
   * acknowledgements. A `count` of 0 removes the cap.
   *
   * {{{
   * import zio._
   * def setPrefetch(channel: Channel): ZIO[Any, Throwable, Unit] =
   *   channel.basicQos(10)
   * }}}
   *
   * @param count
   *   The maximum number of unacknowledged deliveries
   * @param global
   *   True to apply the count to the whole channel, false to apply it to each consumer
   */
  def basicQos(
    count: Int,
    global: Boolean = false
  ): ZIO[Any, Throwable, Unit] =
    withChannelBlocking(_.basicQos(count, global)).unit

  /**
   * Consumes messages from a queue as a stream of deliveries.
   *
   * The stream ends when the broker cancels the consumer, for example when the queue is deleted. It fails with a
   * `ShutdownSignalException` when the connection closes. When the stream ends, the consumer is cancelled with
   * `basicCancel`, and an error from that call is ignored.
   *
   * With `autoAck = false` (the default), each delivery must be acknowledged with `ack` or rejected with `nack`.
   * Deliveries that are not acknowledged go back to the queue when the connection closes.
   *
   * {{{
   * import zio._
   * import zio.amqp.model._
   * import zio.stream.ZStream
   *
   * def processOrders(channel: Channel): ZIO[Any, Throwable, Unit] =
   *   channel
   *     .consume(QueueName("orders"), ConsumerTag("orders-worker"))
   *     .mapZIO(delivery => channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag)))
   *     .runDrain
   * }}}
   *
   * @param queue
   *   Name of the queue to consume from
   * @param consumerTag
   *   Tag that identifies the consumer. It is used to cancel the consumer
   * @param autoAck
   *   True to acknowledge each delivery as soon as the broker sends it
   * @return
   *   A stream of the deliveries
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

  /**
   * Acknowledges a delivery, so the broker removes the message from its queue. With `multiple = true`, the broker also
   * acknowledges every unacknowledged delivery on this channel with a smaller delivery tag.
   *
   * {{{
   * import zio._
   * import com.rabbitmq.client.Delivery
   * import zio.amqp.model._
   *
   * def acknowledge(channel: Channel, delivery: Delivery): ZIO[Any, Throwable, Unit] =
   *   channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
   * }}}
   *
   * @param deliveryTag
   *   Delivery tag of the message to acknowledge
   * @param multiple
   *   True to also acknowledge all unacknowledged deliveries with a smaller delivery tag
   */
  def ack(deliveryTag: DeliveryTag, multiple: Boolean = false): ZIO[Any, Throwable, Unit] =
    withChannel(c =>
      attemptBlocking(
        c.basicAck(deliveryTag, multiple)
      )
    )

  /**
   * Acknowledges the given deliveries with one call, by acknowledging up to the largest delivery tag with
   * `multiple = true`. Like `ack` with `multiple = true`, it also acknowledges any other unacknowledged delivery on
   * this channel with a smaller delivery tag, including tags that are not in `deliveryTags`.
   *
   * Throws a `java.lang.UnsupportedOperationException` when `deliveryTags` is empty. The exception is thrown when the
   * method is called, not when the returned effect runs.
   *
   * @param deliveryTags
   *   Delivery tags of the messages to acknowledge
   */
  def ackMany(deliveryTags: Seq[DeliveryTag]): ZIO[Any, Throwable, Unit] =
    ack(deliveryTags.max[Long], multiple = true)

  /**
   * Rejects a delivery. With `requeue = true`, the broker puts the message back on its queue. With `requeue = false`
   * (the default), the broker discards the message, unless its queue has a dead-letter exchange. With
   * `multiple = true`, the rejection also covers every unacknowledged delivery on this channel with a smaller delivery
   * tag.
   *
   * @param deliveryTag
   *   Delivery tag of the message to reject
   * @param requeue
   *   True to put the message back on its queue
   * @param multiple
   *   True to also reject all unacknowledged deliveries with a smaller delivery tag
   */
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

  /**
   * Rejects the given deliveries with one call, by rejecting up to the largest delivery tag with `multiple = true`.
   * Like `nack` with `multiple = true`, it also rejects any other unacknowledged delivery on this channel with a
   * smaller delivery tag.
   *
   * Throws a `java.lang.UnsupportedOperationException` when `deliveryTags` is empty. The exception is thrown when the
   * method is called, not when the returned effect runs.
   *
   * @param deliveryTags
   *   Delivery tags of the messages to reject
   * @param requeue
   *   True to put the messages back on their queue
   */
  def nackMany(deliveryTags: Seq[DeliveryTag], requeue: Boolean = false): ZIO[Any, Throwable, Unit] =
    nack(deliveryTags.max[Long], requeue, multiple = true)

  /**
   * Publishes a message to an exchange. The exchange routes the message by its `routingKey`. The default exchange, with
   * the empty name `ExchangeName("")`, routes the message to the queue whose name equals the routing key.
   *
   * {{{
   * import zio._
   * import com.rabbitmq.client.MessageProperties
   * import zio.amqp.model._
   *
   * def publishOrder(channel: Channel, json: String): ZIO[Any, Throwable, Unit] =
   *   channel.publish(
   *     ExchangeName("orders"),
   *     json.getBytes("UTF-8"),
   *     RoutingKey("order.eu.created"),
   *     props = MessageProperties.PERSISTENT_BASIC
   *   )
   * }}}
   *
   * @param exchange
   *   Name of the exchange to publish to
   * @param body
   *   Body of the message
   * @param routingKey
   *   Routing key of the message
   * @param mandatory
   *   True to ask the broker to return a message that no queue receives. This channel registers no return listener, so
   *   the call succeeds whether or not a queue receives the message
   * @param immediate
   *   Not supported by RabbitMQ: with `true`, the broker closes the connection with a `NOT_IMPLEMENTED` error (reply
   *   code 540)
   * @param props
   *   Message properties, such as `MessageProperties.PERSISTENT_BASIC`, which asks the broker to persist the message
   */
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

  /**
   * Returns the number of messages in a queue ready to be delivered to consumers. This method assumes the queue exists.
   * If it doesn't, the broker closes the channel with a `NOT_FOUND` error (reply code 404).
   *
   * @param queue
   *   the name of the queue
   * @return
   *   the number of messages in ready state
   */
  def messageCount(queue: QueueName): ZIO[Any, Throwable, Long] = withChannelBlocking { c =>
    c.messageCount(QueueName.unwrap(queue))
  }

  /**
   * Returns the number of consumers on a queue. This method assumes the queue exists. If it doesn't, the broker closes
   * the channel with a `NOT_FOUND` error (reply code 404).
   *
   * @param queue
   *   the name of the queue
   * @return
   *   the number of consumers
   */
  def consumerCount(queue: QueueName): ZIO[Any, Throwable, Long] = withChannelBlocking { c =>
    c.consumerCount(QueueName.unwrap(queue))
  }

  /**
   * Purges the contents of the given queue.
   *
   * @param queue
   *   the name of the queue
   * @return
   *   purge-confirm if the purge was executed successfully. Its `getMessageCount` is the number of messages that were
   *   purged
   */
  def purgeQueue(queue: QueueName): ZIO[Any, Throwable, PurgeOk] = withChannelBlocking { c =>
    c.queuePurge(QueueName.unwrap(queue))
  }

  private[amqp] def withChannel[T](f: RChannel => Task[T]) =
    access.withPermit(f(channel))

  private[amqp] def withChannelBlocking[R, T](f: RChannel => T) =
    access.withPermit(attemptBlocking(f(channel)))
}

/**
 * Opens connections and channels to a RabbitMQ broker.
 */
object Amqp {

  /**
   * Creates a Connection
   *
   * The connection is closed when the enclosing `Scope` ends. If the broker has already closed the connection, closing
   * it fails with a defect (`AlreadyClosedException`) when the scope ends.
   *
   * @param factory
   *   Connection factory
   * @return
   *   Connection as a managed resource
   */
  def connect(factory: ConnectionFactory): ZIO[Scope, Throwable, Connection] =
    ZIO.acquireRelease(attemptBlocking(factory.newConnection()))(c => ZIO.attempt(c.close()).orDie)

  /**
   * Creates a Connection to the broker at `uri`. The URI must use the `amqp` or `amqps` scheme. The credentials, host,
   * port and virtual host are read from the URI.
   *
   * Throws a `java.lang.IllegalArgumentException` when the URI can't be used, for example when its scheme is not `amqp`
   * or `amqps`. The exception is thrown when the method is called, not when the returned effect runs.
   *
   * {{{
   * import zio._
   * import com.rabbitmq.client.Connection
   * import java.net.URI
   *
   * def localConnection: ZIO[Scope, Throwable, Connection] =
   *   Amqp.connect(new URI("amqp://guest:guest@localhost:5672/%2F"))
   * }}}
   *
   * @param uri
   *   AMQP URI of the broker
   * @return
   *   Connection as a managed resource
   */
  def connect(uri: URI): ZIO[Scope, Throwable, Connection] = {
    val factory = new ConnectionFactory()
    factory.setUri(uri)
    connect(factory)
  }

  /**
   * Creates a Connection with the settings of an `AMQPConfig`. The user and the password are written into the URI
   * without percent-encoding. A `/` in the password makes this method throw a `java.lang.IllegalArgumentException` when
   * it is called, not when the returned effect runs.
   *
   * {{{
   * import com.rabbitmq.client.Connection
   * import zio._
   * import zio.amqp.model._
   *
   * val config: AMQPConfig = AMQPConfig.default.copy(host = "rabbit.example.com", ssl = true)
   *
   * val connection: ZIO[Scope, Throwable, Connection] = Amqp.connect(config)
   * }}}
   *
   * @param amqpConfig
   *   Settings of the connection
   * @return
   *   Connection as a managed resource
   */
  def connect(amqpConfig: AMQPConfig): ZIO[Scope, Throwable, Connection] = {
    val factory = new ConnectionFactory()
    factory.setUri(amqpConfig.toUri)
    connect(factory)
  }

  /**
   * Creates a Channel that is safe for concurrent access
   *
   * The channel is closed when the enclosing `Scope` ends. Its methods take turns on the RabbitMQ channel, so the
   * channel can be shared between fibers. If the broker has already closed the channel, for example after a `NOT_FOUND`
   * error from `queueDeclarePassive`, closing it fails with a defect (`AlreadyClosedException`) when the scope ends.
   *
   * {{{
   * import zio._
   * import zio.amqp.model._
   *
   * val program: ZIO[Scope, Throwable, Long] =
   *   for {
   *     connection <- Amqp.connect(AMQPConfig.default)
   *     channel    <- Amqp.createChannel(connection)
   *     count      <- channel.messageCount(QueueName("orders"))
   *   } yield count
   * }}}
   *
   * @param connection
   *   Connection to open the channel on
   * @return
   *   Channel as a managed resource
   */
  def createChannel(connection: Connection): ZIO[Scope, Throwable, Channel] =
    (for {
      channel <- ZIO.attempt(connection.createChannel())
      permit  <- Semaphore.make(1)
    } yield new Channel(channel, permit)).withFinalizer(_.withChannel(c => attemptBlocking(c.close())).orDie)

}
