---
title: Building a Kafka Consumer with ZIO Streams
excerpt: Step-by-step guide to building a resilient, backpressure-aware Kafka consumer using ZIO Streams and zio-kafka.
tags:
  - Scala
  - Kafka
  - ZIO Streams
  - Event-Driven
publishedAt: "2024-09-18"
readingMinutes: 10
---

## Why ZIO Streams for Kafka?

Kafka consumers have a natural streaming shape: an unbounded sequence of records flowing from the broker. ZIO Streams models this perfectly — it gives you backpressure, composable operators, and resource safety all in one abstraction.

`zio-kafka` wraps the official Java Kafka client in a purely functional interface. You get:

- **Backpressure by design** — the stream only polls as fast as you process
- **Automatic offset management** — commit after processing, not before
- **Consumer group support** — partition rebalancing handled transparently
- **Typed errors** — retries and dead-letter queues fit naturally

## Basic Consumer

```scala
import zio.kafka.consumer.*
import zio.kafka.serde.*

val stream =
  Consumer
    .plainStream(Subscription.topics("events"), Serde.string, Serde.string)
    .mapZIO { record =>
      processRecord(record.value).as(record.offset)
    }
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
```

## Error Handling with Retries

```scala
val resilientStream =
  Consumer
    .plainStream(Subscription.topics("events"), Serde.string, Serde.string)
    .mapZIO { record =>
      processRecord(record.value)
        .retry(Schedule.exponential(100.millis) && Schedule.recurs(3))
        .orElse(deadLetter(record))
        .as(record.offset)
    }
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
```

## Parallel Processing

```scala
val parallelStream =
  Consumer
    .plainStream(Subscription.topics("events"), Serde.string, Serde.string)
    .mapZIOPar(16) { record =>
      processRecord(record.value).as(record.offset)
    }
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
```

`mapZIOPar(n)` runs up to `n` effects concurrently while preserving offset ordering. Backpressure propagates automatically — if processing slows down, polling slows down too.

## Providing the Layer

```scala
val run =
  resilientStream
    .runDrain
    .provide(
      Consumer.live,
      ZLayer.succeed(
        ConsumerSettings(List("localhost:9092"))
          .withGroupId("my-group")
      ),
    )
```
