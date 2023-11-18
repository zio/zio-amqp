---
id: index
title: "ZIO AMQP"
---

ZIO AMQP is a ZIO-based wrapper around the RabbitMQ client. It provides a streaming interface to AMQP queues and helps to prevent you from shooting yourself in the foot with thread-safety issues. 

@PROJECT_BADGES@

## Installation

Add the following lines to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-amqp" % "@VERSION@"
```

## Consuming

The example below creates a connection to an AMQP server and then creates a channel. Both are created as Managed resources, which means they are closed automatically after using even in the face of errors.

The example then creates a stream of the messages consumed from a queue named `"queueName"`. Each received message is acknowledged back to the AMQP server.

## Producing

Also in the example bellow is a producer which publishes to a given queue

```scala
import zio.amqp._
import zio.amqp.model._
import java.net.URI
import zio._
import zio.Console._

val channel: ZIO[Scope, Throwable, Channel] = for {
  connection <- Amqp.connect(URI.create("amqp://my_amqp_server_uri"))
  channel    <- Amqp.createChannel(connection)
} yield channel

val effect: ZIO[Any, Throwable, Unit] =
  ZIO.scoped {
    channel.flatMap { channel =>
      channel
        .consume(queue = QueueName("queueName"), consumerTag = ConsumerTag("test"))
        .mapZIO { record =>
          val deliveryTag = record.getEnvelope.getDeliveryTag
          printLine(s"Received ${deliveryTag}: ${new String(record.getBody)}") *>
            channel.ack(DeliveryTag(deliveryTag))
        }
        .take(5)
        .runDrain
    }
  }

val producer = ZIO.scoped {
  channel.flatMap { channel =>
    channel.publish(
      exchange = ExchangeName(""),
      routingKey = RoutingKey("queueName"),
      body = "Hello world".getBytes
    )
  }
}
```

See the [ZIO documentation](https://zio.dev/docs/overview/overview_running_effects#defaultruntime) for more information on how to run this effect or integrate with an existing application. 
