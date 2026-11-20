---
id: index
title: "Introduction to ZIO AMQP"
sidebar_label: "Introduction"
---

ZIO AMQP is a ZIO-based wrapper around the [RabbitMQ Java client](https://www.rabbitmq.com/client-libraries/java-api-guide). It exposes queues as `ZStream`s of deliveries and serializes access to the underlying RabbitMQ channel, so concurrent fibers cannot corrupt it. Connections and channels are scoped resources: they are closed automatically when the `Scope` that owns them ends, even if the program fails or is interrupted.

@PROJECT_BADGES@

## Installation

Add the following to your `build.sbt` file. ZIO AMQP is published for Scala 2.12, 2.13 and 3, on the JVM only:

```scala
// ZIO AMQP: ZIO wrapper around the RabbitMQ Java client (JVM)
libraryDependencies += "dev.zio" %% "zio-amqp" % "@VERSION@"
```

ZIO AMQP depends on ZIO 2, ZIO Prelude and the RabbitMQ Java client, which sbt resolves automatically.

## Where to Go Next

Start with [Getting Started](./getting-started.md), which connects to a broker, publishes a message and consumes it back. To understand the ideas behind the API, read [AMQP Basics](./explanation/amqp-basics.md). To look up a type or method, use the [reference](./reference/amqp.md).
