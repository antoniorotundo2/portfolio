---
title: "ZIO 2 Fibers: Concurrency Without the Pain"
excerpt: "A deep dive into ZIO fibers, structured concurrency, and why they make concurrent code actually maintainable."
tags: [Scala, ZIO, Concurrency, Fibers]
publishedAt: "2024-11-15"
readingMinutes: 8
---

## What are Fibers?

Fibers are ZIO's lightweight concurrency primitive. Unlike OS threads, fibers are **virtual** — managed entirely by the ZIO runtime. You can spawn millions of them without exhausting system resources, because they don't map 1:1 to JVM threads.

Think of fibers as green threads: cooperative, cheap to create, and automatically multiplexed across a small pool of real threads.

## Structured Concurrency

The key insight of ZIO's concurrency model is **structured concurrency**: child fibers cannot outlive their parent scope. When a scope closes, all child fibers are automatically interrupted. This eliminates entire classes of resource leak bugs that plague unstructured concurrency models.

```scala
for {
  fiber1 <- ZIO.sleep(1.second).fork
  fiber2 <- ZIO.sleep(2.seconds).fork
  _      <- fiber1.join
  _      <- fiber2.join
} yield ()
```

If the parent effect is interrupted, both `fiber1` and `fiber2` are interrupted too — automatically, with no manual cleanup needed.

## Forking and Joining

`ZIO.fork` spawns a fiber and returns a `Fiber[E, A]` handle immediately. The fiber runs in the background while the parent continues. `fiber.join` waits for it to complete and returns its result.

```scala
val program =
  for {
    fiber  <- expensiveComputation.fork
    result <- doOtherWork
    value  <- fiber.join
  } yield (result, value)
```

## Racing Fibers

Sometimes you want whichever fiber finishes first:

```scala
val fast = ZIO.sleep(100.millis).as("fast")
val slow = ZIO.sleep(1.second).as("slow")

val winner = fast.race(slow) // returns "fast"
```

The losing fiber is automatically interrupted.

## Interruption

ZIO fibers support **safe interruption**: when interrupted, a fiber runs any registered finalizers before terminating. This guarantees resources are always released, even in the middle of an operation.

```scala
val safe =
  ZIO.acquireReleaseWith(openConnection)(closeConnection) { conn =>
    useConnection(conn)
  }
```

Even if `safe` is interrupted while `useConnection` is running, `closeConnection` is guaranteed to execute.

## When to Use Fibers Directly

Most of the time you don't need to manage fibers manually. ZIO provides higher-level combinators:

- `ZIO.zipPar` — run two effects in parallel, wait for both
- `ZIO.foreachPar` — parallel `foreach` with bounded concurrency
- `ZIO.race` — first to succeed wins

Reach for raw `fork`/`join` only when you need precise control over fiber lifecycle.
