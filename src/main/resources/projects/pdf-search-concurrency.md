---
id: "pdf-search-concurrency"
title: "Concurrent PDF Word Search"
description: "A Java CLI + GUI that searches a keyword across PDF trees, implemented with five concurrency paradigms and formally verified in TLA+"
tags:
  - Java
  - Concurrency
  - Akka
  - Vert.x
  - RxJava
  - TLA+
  - PDFBox
status: "archived"
year: 2025
---

A command-line tool (plus an optional GUI) that **recursively searches a keyword inside every PDF** in a directory tree. The twist: the *same* problem is solved with **five different concurrency paradigms**, so their structure, complexity and performance can be compared head-to-head. Text extraction is handled by **Apache PDFBox**, and the whole thing runs on the JVM (Windows, Linux, macOS).

The non-functional goals drove the design: scale across cores for low latency, stay responsive via asynchronous models, survive corrupted PDFs without crashing, and keep each paradigm cleanly isolated so new ones can be added without invasive refactoring.

## Architecture

The system is a **modular pipeline with a selectable orchestration layer** — the concurrency paradigm. A central CLI entry point parses the arguments (root path, keyword, paradigm) and dispatches to the chosen engine through a single switch, while a `PDFSearchLogger` singleton collects metrics and reports. The functional core is reused unchanged; only the coordination model varies.

![CLI architecture: a shared core with one engine per paradigm](/static/img/pcd_architecture.png)

## Five concurrency paradigms

Each engine implements the same producer/consumer search with a different model:

- **Threads** — explicit `Thread`s with a monitor-protected shared queue (`ReentrantLock` + `Condition`): a directory scanner produces PDF paths, a pool of workers consumes and matches them.
- **Task / Executor** — an `ExecutorService` + `CompletionService` pool.
- **Event-Driven** — Vert.x verticles communicating over the **EventBus**.
- **Actors** — **Akka Typed**, with a master actor centralising aggregation (no locks).
- **Reactive** — **RxJava** streams with `flatMap(maxConcurrency)` for backpressure.

A cross-cutting comparison highlights the trade-offs:

| Aspect | Threads | Task | Event | Actors | Reactive |
|---|---|---|---|---|---|
| Decoupling | Low | Medium | High | High | High |
| Supervision | Manual | Manual | Limited | Native | Operators |
| Backpressure | Manual | Pool size | Custom | Mailbox | Native |
| Extensibility | Medium | Medium | High | High | Very high |
| Debuggability | High | High | Medium | Medium | Medium-low |
| Overhead | Low | Medium | Medium | Medium | Medium-low |

## Formal verification with TLA+

The thread-based producer/consumer was specified in **TLA+** and checked with the **TLC** model checker, proving the implementation is free of **deadlock and livelock**, always **terminates** after processing every file, never **loses** a queued file, and keeps shared data **consistent**. Java components map directly onto the spec (e.g. `DirectoryScannerThread` → `ProduceAndMaybeFinish`, the worker pool → `Consume`, the shared queue → `queue`).

## CLI + GUI

The CLI is the core, packaged as an executable JAR. A separate **Swing GUI** improves usability when a desktop environment is available, and is deliberately decoupled: it simply **launches the CLI as a subprocess** for the selected paradigm, so either component can be updated independently.

![The GUI running a thread-based search with live per-worker logs](/static/img/pcd_gui.png)

## Performance

The paradigms were benchmarked on a public dataset of **1,076 PDFs** (the keyword *"research"* appears in 195 of them), on an Apple **M1 Pro** (8 cores). Wall-clock search times:

| Paradigm | Search time (s) |
|---|---|
| Threads | 14.937 |
| Task | 27.392 |
| Event | 17.750 |
| **Actors** | **14.252** |
| Reactive | 17.434 |

![Search time per concurrency paradigm](/static/img/pcd_performance.png)

The **Actor** model came out fastest: its master actor centralises result aggregation, avoiding lock contention, while Akka's dispatcher balances the load well. **Threads** are a close second thanks to their minimal overhead. The **Task** model lagged — a sign of sub-optimal pool sizing and dispatch overhead on many small PDFs — while **Event** and **Reactive** paid a moderate cost for message marshaling and an initial single-threaded directory scan respectively.

## Takeaways

There is no universally "best" paradigm: manual threads stay relevant as a baseline for understanding the fundamentals, tasks are the pragmatic default, Vert.x shines for event-oriented and distributable systems, actors offer the strongest resilience and isolation, and reactive streams give the most composable, backpressure-aware pipelines. Picking one is ultimately a function of the project's goals — performance, resilience, extensibility or sheer simplicity.
