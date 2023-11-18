[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
[//]: # (So please do not edit it manually. Instead, change "docs/index.md" file or sbt setting keys)
[//]: # (e.g. "readmeDocumentation" and "readmeSupport".)

# ZIO AMQP

ZIO AMQP is a ZIO-based wrapper around the RabbitMQ client. It provides a streaming interface to AMQP queues and helps to prevent you from shooting yourself in the foot with thread-safety issues. 

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-amqp/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-amqp_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-amqp_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-amqp_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-amqp_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-amqp-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-amqp-docs_2.13) [![ZIO AMQP](https://img.shields.io/github/stars/zio/zio-amqp?style=social)](https://github.com/zio/zio-amqp)

## Installation

Add the following lines to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-amqp" % "1.0.0-alpha.2"
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

## Documentation

Learn more on the [ZIO AMQP homepage](https://zio.dev/zio-amqp)!

## Contributing

For the general guidelines, see ZIO [contributor's guide](https://zio.dev/contributor-guidelines).

## Code of Conduct

See the [Code of Conduct](https://zio.dev/code-of-conduct)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].

[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"

## License

[License](LICENSE)
