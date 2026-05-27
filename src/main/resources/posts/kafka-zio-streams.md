---
title: "Building a Kafka Consumer with ZIO Streams"
excerpt: "Step-by-step guide to building a resilient, backpressure-aware Kafka consumer using ZIO Streams and zio-kafka."
tags: [Scala, Kafka, ZIO Streams, Event-Driven]
publishedAt: "2024-09-18"
readingMinutes: 10
---

## Why ZIO Streams for Kafka?

Kafka consumers have a natural streaming shape: an unbounded sequence of records flowing from the broker. ZIO Streams models this perfectly — it gives you backpressure, composable operators, and resource safety all in one abstraction.

`zio-kafka` wraps the official Java Kafka client in a purely functional interface built on ZIO Streams. You get:

- **Backpressure by design** — the stream only polls as fast as you process
- **Automatic offset management** — commit offsets after processing, not before
- **Consumer group support** — partition rebalancing handled transparently
- **Error handling** — typed errors, retries, and dead-letter queues fit naturally

## Setting Up

Add `zio-kafka` to your build:

```scala
"dev.zio" %% "zio-kafka" % "2.7.4"
```

## Basic Consumer

```scala
import zio.*
import zio.kafka.consumer.*
import zio.kafka.serde.*

val consumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("my-group")
    .withClientId("my-consumer")

val stream =
  Consumer
    .plainStream(Subscription.topics("events"), Serde.string, Serde.string)
    .mapZIO { record =>
      processRecord(record.value).as(record.offset)
    }
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
```

The key insight is the separation between processing and committing. We process the record, collect its offset, and commit in batches — maximising throughput without risking message loss.

## Error Handling

Wrap processing in a retry policy to handle transient failures:

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

Failed records after exhausting retries are sent to a dead-letter topic — they don't block the stream.

## Parallelism

ZIO Streams makes it easy to process records in parallel while preserving offset ordering:

```scala
val parallelStream =
  Consumer
    .plainStream(Subscription.topics("events"), Serde.string, Serde.string)
    .mapZIOPar(16) { record =>           // up to 16 concurrent effects
      processRecord(record.value).as(record.offset)
    }
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
```

`mapZIOPar(n)` runs up to `n` effects concurrently. The stream automatically handles backpressure — if processing slows down, polling slows down too.

## Providing the Layer

```scala
val run =
  resilientStream
    .runDrain
    .provide(
      Consumer.live,
      ZLayer.succeed(consumerSettings),
    )
```

The `Consumer.live` layer manages the Kafka client lifecycle — it opens on startup and closes cleanly on shutdown, committing any pending offsets.

## Testing

zio-kafka provides an in-memory Kafka implementation for testing:

```scala
import zio.kafka.testkit.*

test("consumer processes records") {
  for {
    _       <- Producer.produce("events", "key", "value", Serde.string, Serde.string)
    record  <- myConsumerStream.take(1).runCollect
  } yield assert(record.head.value)(equalTo("value"))
}.provide(KafkaTestUtils.kafkaLayer)
```

No external Kafka broker needed — the test layer spins up an embedded instance automatically.
