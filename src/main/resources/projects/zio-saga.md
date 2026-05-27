---
id: zio-saga
title: ZIO Saga
description: Distributed saga pattern implementation for ZIO
tags:
  - Scala
  - ZIO
  - Distributed Systems
  - Open Source
githubUrl: https://github.com/example/zio-saga
status: active
year: 2024
---

A library implementing the **Saga pattern** for managing distributed transactions in ZIO applications.

Provides compensating transactions, parallel execution, and full observability out of the box. Each saga step is a ZIO effect; rollback logic is declared alongside the forward action, keeping the two co-located and easy to reason about.

Key features:

- Type-safe saga definition with ZIO effects
- Automatic compensating transaction execution on failure
- Parallel saga steps with configurable concurrency
- Built-in retry policies per step
- OpenTelemetry tracing integration
