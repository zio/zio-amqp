# Plan: ZIO AMQP documentation (ZIO-42)

Working file. Delete before the PR is opened.

## The problem

ZIO AMQP has one short docs page (`docs/index.md`). It does not teach a newcomer how to build anything, and it does not describe the API. Parts of it are also out of date: it says "Managed resources" (a ZIO 1 term; ZIO 2 calls them scoped resources), the "Consuming" section has no code of its own, and it links to an old ZIO docs URL.

## The goal

A reader can (1) learn the library by building a small app, (2) solve common tasks from short guides, and (3) look up every public method and type.

## Findings

- **API size.** Small: about 400 lines. `Amqp` object (`connect` x3, `createChannel`), `Channel` class (16 methods: `queueDeclare`, `queueDeclarePassive`, `queueDelete`, `exchangeDeclare`, `exchangeDelete`, `queueBind`, `basicQos`, `consume`, `ack`, `ackMany`, `nack`, `nackMany`, `publish`, `messageCount`, `consumerCount`, `purgeQueue`), `AMQPConfig`, `ExchangeType`, and the newtypes in `model/package.scala`.
- **Master vs 1.0.0.** The public API is identical. The only change in `zio-amqp/src/main` since v1.0.0 is a type annotation on `AMQPConfig.default`. So the docs describe what users can install today.
- **Current docs.** `docs/index.md` (66 lines), `docs/sidebars.js` has an empty `items` list. The mdoc project `docs` exists in `build.sbt`.
- **Publishing.** zio.dev pins `@zio.dev/zio-amqp` 1.0.0-alpha.3, and npm has only alpha.2 and alpha.3. The last release is v1.0.0 (2023-11-18). New docs reach zio.dev only after a new release with a green Release Docs job, plus a version-pin bump in zio/zio. This is not part of this task.
- **Things I noticed while reading the source, to be documented honestly, not fixed:**
  - `consume` exposes the raw RabbitMQ `Delivery`; the delivery tag is `record.getEnvelope.getDeliveryTag` (a `Long`), which must be wrapped in `DeliveryTag(...)` for `ack`.
  - `ackMany` / `nackMany` ack the highest tag with `multiple = true`; they throw on an empty `Seq` (`.max`).
  - `Amqp.connect(AMQPConfig)` goes through a URI, and `AMQPConfig.port` is a `Short`.
  - `queueDeclare` with an empty name gets a server-generated name.
  - Closing the stream of `consume` cancels the consumer (`basicCancel`).

## Page list (sidebar order)

All pages go under `docs/`. Size is a rough line count.

| # | Page | Type | Based on | Size |
|---|------|------|----------|------|
| 1 | `index.md` (rewrite) | overview | `Client.scala`, existing page | ~100 |
| 2 | `tutorial-work-queue.md` | tutorial | `Client.scala`, `AmqpClientSpec.scala`, `docker-compose.yml` | ~250 |
| 3 | `how-to-connect.md` | how-to | `Amqp.connect` (3 overloads), `AMQPConfig` | ~120 |
| 4 | `how-to-acknowledge-messages.md` | how-to | `consume`, `ack`, `nack`, `ackMany`, `nackMany` | ~150 |
| 5 | `how-to-limit-prefetch.md` | how-to | `basicQos`, `consume` | ~100 |
| 6 | `how-to-route-with-exchanges.md` | how-to | `exchangeDeclare`, `queueBind`, `publish`, `ExchangeType` | ~180 |
| 7 | `how-to-publish-durable-messages.md` | how-to | `queueDeclare(durable)`, `publish(props)` with `AMQP.BasicProperties` | ~120 |
| 8 | `how-to-stop-a-consumer.md` | how-to | `consume` (`ensuring` cancel), scopes | ~120 |
| 9 | `how-to-test-with-testcontainers.md` | how-to | `AmqpClientSpec.scala` | ~130 |
| 10 | `reference/amqp.md` (`Amqp` and `AMQPConfig`) | reference | `Client.scala`, `AMQPConfig.scala` | ~200 |
| 11 | `reference/channel.md` | reference | `Client.scala` | ~450 |
| 12 | `reference/model.md` (newtypes, `ExchangeType`) | reference | `model/` | ~180 |

Every public method of `Channel` and `Amqp` appears in a reference page with its real signature. The how-tos link to the reference, and the reference links back.

## Examples policy

All code is inline `mdoc:compile-only`. It is type-checked against the real API on every build, but no broker is needed (CI's docs job has no RabbitMQ). The tutorial also ends with a complete program that the reader can paste into a file and run against `docker compose up -d`. I would not add separate example files: the API is small and inline blocks cover it.

## PR split

Option A (recommended): one PR with everything, one commit per page. 12 pages, about 2,000 lines of docs, but no code changes.
Option B: two PRs on this branch, in order: (1) index + reference + connect how-to, (2) tutorial + remaining how-tos.

## Branching

Stay on `amqp-docs`. No other branches. If option B, PR 2 is pushed to the same branch after PR 1 is merged.

## Verification before each PR

`sbt docs/mdoc`, `sbt scalafmtAll`, `sbt "++2.13; check"` (or `sbt lint`), `sbt docs/buildWebsite`. Sidebar lists every page, every link target exists, this plan file is not in the PR.
