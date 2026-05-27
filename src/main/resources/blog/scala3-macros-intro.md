---
title: "Scala 3 Macros: Generate Code at Compile Time"
excerpt: "How to use Scala 3's new macro system to eliminate boilerplate and catch errors before your program ever runs."
tags:
  - Scala 3
  - Macros
  - Metaprogramming
publishedAt: "2024-10-02"
readingMinutes: 12
---

## Why Macros?

Macros let you run Scala code **at compile time** — inspecting and generating AST nodes before the compiler produces bytecode. The use cases are compelling:

- Derive type class instances without runtime reflection
- Validate string literals (regex, SQL, URLs) at compile time
- Generate boilerplate: codecs, lenses, printers
- Build DSLs with precise error messages

Scala 3 introduced a completely redesigned macro system that is safer, more principled, and easier to debug than the Scala 2 equivalent.

## Inline and Transparent

The simplest form of compile-time computation is `inline`:

```scala
inline def log(inline msg: String): Unit =
  println(s"[${compiletime.codeOf(msg)}] $msg")
```

`inline` functions are expanded at call sites — no function call overhead, and the body can inspect the static shape of its arguments.

`transparent inline` goes further: the return type is refined to the actual computed type at each call site:

```scala
transparent inline def choose(b: Boolean): Any =
  inline if b then 42 else "hello"

val x = choose(true)   // x: Int = 42
val y = choose(false)  // y: String = "hello"
```

## Compile-Time Assertions

You can reject invalid calls with a precise compiler error:

```scala
inline def assertPositive(inline n: Int): Int =
  inline if n > 0 then n
  else compiletime.error("n must be positive at compile time")

val ok  = assertPositive(5)   // compiles
val bad = assertPositive(-1)  // error: n must be positive at compile time
```

Zero runtime cost — the check happens entirely during compilation.

## Quotes and Splices

For full macro power, Scala 3 provides **quotes** (`'{ ... }`) and **splices** (`${ ... }`):

```scala
import scala.quoted.*

inline def debug[A](inline a: A): A = ${ debugImpl('a) }

def debugImpl[A: Type](a: Expr[A])(using Quotes): Expr[A] =
  '{
    val result = $a
    println(s"${${ Expr(a.show) }} = $result")
    result
  }
```

## Deriving Type Classes

The most practical use of macros in day-to-day Scala is type class derivation:

```scala
case class User(name: String, age: Int) derives JsonCodec
```

The `derives` clause invokes a macro that inspects `User`'s fields at compile time and generates the codec — no reflection, no runtime overhead.
