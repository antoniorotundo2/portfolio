---
id: stream-processor
title: Real-time Stream Processor
description: High-throughput event processing pipeline with Kafka + ZIO Streams
tags:
  - Scala
  - Kafka
  - ZIO Streams
  - PostgreSQL
githubUrl: https://github.com/example/stream-processor
status: active
year: 2024
---

An event-driven processing pipeline capable of ingesting **500k events/sec**, built on ZIO Streams and Kafka.

The pipeline handles exactly-once semantics via transactional producers and idempotent consumers, with automatic backpressure propagation from the sink back to the Kafka poll loop.

Highlights:

- Exactly-once delivery with Kafka transactions
- Backpressure-aware consumption via ZIO Streams
- PostgreSQL sink with batched writes and conflict resolution
- Prometheus metrics for lag, throughput, and error rates
- Kubernetes-native: horizontal scaling via consumer group rebalancing
