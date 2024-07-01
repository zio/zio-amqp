package zio.amqp

import com.rabbitmq.client.{ AMQP, ConfirmListener }
import zio._
import zio.amqp.ConfirmsChannel._
import zio.amqp.model._

import scala.collection.immutable.TreeMap

final class ConfirmsChannel private[amqp] (
  channel: com.rabbitmq.client.Channel,
  access: Semaphore,
  queue: Queue[ConfirmsMessage],
  map: Ref[TreeMap[Long, PendingConfirmationMessage]]
) extends Channel(channel, access) {
  def getQueue: Queue[ConfirmsMessage] = queue

  override def publish(
    exchange: ExchangeName,
    body: Array[Byte],
    routingKey: RoutingKey = RoutingKey(""),
    mandatory: Boolean = false,
    immediate: Boolean = false,
    props: AMQP.BasicProperties = new AMQP.BasicProperties()
  ): ZIO[Any, Throwable, Unit] =
    withChannel(c =>
      ZIO.attemptBlocking {
        val id = c.getNextPublishSeqNo()
        c.basicPublish(
          ExchangeName.unwrap(exchange),
          RoutingKey.unwrap(routingKey),
          mandatory,
          immediate,
          props,
          body
        )
        id -> body
      }.flatMap { case (id, content) => map.update(_.updated(id, PendingConfirmationMessage(id, content))) }
    )
}

object ConfirmsChannel {

  final case class PendingConfirmationMessage(deliveryTag: Long, content: Array[Byte])

  private[amqp] def listener(
    queue: Queue[ConfirmsMessage],
    map: Ref[TreeMap[Long, PendingConfirmationMessage]]
  ): ConfirmListener =
    new ConfirmListener {

      override def handleAck(deliveryTag: Long, multiple: Boolean): Unit =
        Unsafe.unsafe(implicit u =>
          zio.Runtime.default.unsafe.run {
            ZIO.logDebug(s"Received Ack for tag $deliveryTag") *>
              map
                .getAndUpdate(_ - deliveryTag)
                .flatMap(entry =>
                  entry.get(deliveryTag) match {
                    case None        => ZIO.logWarning(s"Message for delievery tag $deliveryTag not found")
                    case Some(value) => queue.offer(ConfirmsMessage.Ack(deliveryTag, value.content)).ignore
                  }
                )
          }.getOrThrowFiberFailure()
        )

      override def handleNack(deliveryTag: Long, multiple: Boolean): Unit =
        Unsafe.unsafe(implicit u =>
          zio.Runtime.default.unsafe.run {
            ZIO.logDebug(s"Received Nack for tag $deliveryTag") *>
              map.get.flatMap { tm =>
                tm.get(deliveryTag) match {
                  case None        => ZIO.logWarning(s"Message for delievery tag $deliveryTag not found")
                  case Some(value) => queue.offer(ConfirmsMessage.Nack(deliveryTag, value.content)).ignore
                }
              }
          }.getOrThrowFiberFailure()
        )

    }
}
