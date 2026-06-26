---
id: "distributed-puzzle-akka-rmi"
title: "Distributed Multiplayer Puzzle: Akka vs RMI"
description: "A serverless P2P collaborative puzzle game implemented twice — with Akka distributed actors and with Java RMI — then profiled and compared"
tags:
  - Java
  - Distributed Systems
  - Akka
  - Java RMI
  - P2P
  - Actor Model
status: "archived"
year: 2025
---

A **distributed, peer-to-peer multiplayer puzzle game**: several players collaborate to solve the *same* sliding-tile puzzle, with **no central server**. Every time a player swaps two tiles, the move must propagate to everyone so that all peers converge on the same board. To explore the trade-offs of distributed programming, the exact same game is built **twice** — once with **Akka** (distributed actors) and once with **Java RMI** (remote method invocation) — and the two are profiled head-to-head.

## The challenge

Picture a room full of people solving one puzzle with one shared set of pieces. The core difficulty is keeping a **shared state** (the board) consistent while many peers issue **concurrent moves**, all without a coordinator. The usual distributed concerns appear in different shades depending on the paradigm: **race conditions**, **peer discovery**, **state synchronization**, and **failure detection** of disconnected players.

## Akka: everything is a message

Akka's **Actor Model** sidesteps shared memory entirely. Each peer is an actor with **private, isolated state**, processing messages **one at a time** — so internal race conditions simply cannot happen, with no `synchronized` or locks. Moves travel as **immutable messages** broadcast to known peers:

```java
private void broadcastTileSwap(int position1, int position2) {
    TileSwapMessage message = new TileSwapMessage(
        position1, position2, playerName, System.currentTimeMillis());
    knownPeers.values().forEach(peer ->
        peer.tell(message, getContext().getSelf()));
}
```

Peer discovery rides on **Akka Cluster**: a joining player only needs one seed node's address, and membership is gossiped automatically. Conflicting updates are resolved with a **version number + timestamp**, and **failure detection is native** — the cluster reacts to a peer leaving on its own.

![JProfiler telemetry of the Akka version (memory, CPU, sockets)](/static/img/pcd2_akka_profile.png)

## RMI: remote calls that look local

Java **RMI** takes the opposite stance: define a remote interface, implement it, register it, and call it across the network as if it were local. Discovery and broadcast are built on a **P2P-over-client-server** design with **flooding**. The catch is that remote calls are **synchronous** — the caller blocks until N peers respond — so the project wraps them in `CompletableFuture` to imitate Akka's native asynchrony. Failure detection is hand-rolled with **heartbeat loops, try/catch and peer lists**.

![JProfiler telemetry of the RMI version (memory, CPU, RMI call timings)](/static/img/pcd2_rmi_profile.png)

## Performance

Both versions were profiled with **JProfiler** running **10 local peers** on an Apple **M1 Pro** (JDK 21).

**Akka**

| Metric | Start | Peak | Average |
|---|---|---|---|
| Memory | 23.13 MB | 211 MB | 132.10 MB |
| CPU load | 1.32% | 29.32% | 4.43% |
| Inbound traffic | 21.29 KB | 122.6 KB | 95.84 KB |
| Outbound traffic | 19.41 KB | 125.7 KB | 95.36 KB |

**RMI**

| Metric | Start | Peak | Average |
|---|---|---|---|
| Memory | 43.08 MB | 230.1 MB | 172.9 MB |
| CPU load | 0.20% | 8.32% | 1.3% |
| Client call time | 3.11 ms | 3.62 ms | 3.08 ms |
| Server call time | 2.33 ms | 13.15 ms | 5.73 ms |

![Akka vs RMI: memory and CPU usage](/static/img/pcd2_comparison.png)

A clear trade-off emerges: **Akka uses less memory but more CPU** (actor scheduling plus Jackson message serialization), while **RMI is lighter on CPU but heavier on memory**.

## When to use which

- **Akka** forces you to abandon sequential thinking — everything is a message, every component an isolated actor, concurrency handled implicitly by the scheduler. It costs more upfront but pays back in **resilience, ordering and scalability**, with native cluster membership and failure handling.
- **RMI** gives an almost instant start thanks to the remote-method-call abstraction, but the edge cases — latency, partial failures, blocking, manual recovery — surface as the system grows.

In short: reach for **RMI** when you need something working in days and growth is uncertain; reach for **Akka** when resilience, failure handling and future distributed scaling are central.
