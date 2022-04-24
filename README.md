[![Maven Central](https://img.shields.io/maven-central/v/nl.vroste/zio-amqp_2.13)](https://repo1.maven.org/maven2/nl/vroste/zio-amqp_2.13/) [![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/nl.vroste/zio-amqp_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/nl/vroste/zio-amqp_2.13/)
# ZIO AMQP

ZIO AMQP is a ZIO-based wrapper around the RabbitMQ client. It provides a streaming interface to AMQP queues and helps to prevent you from shooting yourself in the foot with thread-safety issues. 


## Installation

Add to your build.sbt:

```scala
libraryDependencies += "nl.vroste" %% "zio-amqp" % "<version>"
```

The latest version is built against ZIO 2.0.0-RC5.

### Consuming

The example below creates a connection to an AMQP server and then creates a channel. Both are created as Managed resources, which means they are closed automatically after using even in the face of errors.

The example then creates a stream of the messages consumed from a queue named `"queueName"`. Each received message is acknowledged back to the AMQP server.

```scala
import nl.vroste.zio.amqp._
import nl.vroste.zio.amqp.model._
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
```

See the [ZIO documentation](https://zio.dev/docs/overview/overview_running_effects#defaultruntime) for more information on how to run this effect or integrate with an existing application. 
