---
id: type-safe-api
title: Type-Safe REST API Framework
description: Compile-time validated HTTP routes with full schema derivation
tags:
  - Scala 3
  - ZIO HTTP
  - Macros
  - OpenAPI
githubUrl: https://github.com/example/type-safe-api
liveUrl: https://example.dev/type-safe-api
status: active
year: 2023
---

A micro-framework built on ZIO HTTP that derives **OpenAPI specs at compile time** via Scala 3 macros.

Route handlers are typed end-to-end: request body, path parameters, query parameters, and response type are all verified by the compiler. The OpenAPI document is generated as a compile-time artifact — no reflection, no runtime scanning.

Features:

- Fully typed route definitions with zero boilerplate
- Compile-time OpenAPI 3.1 spec generation
- Request/response codec derivation via zio-json
- Swagger UI served automatically in development mode
- First-class support for streaming responses
