# Portfolio — Scala + ZIO

A dark, tech-futuristic personal portfolio built with **Scala 3**, **ZIO 2**, and **ZIO HTTP**.

## Features

| Route | Description |
|---|---|
| `GET /` | Landing page — hero, skills, featured projects, latest posts |
| `GET /projects` | Full projects grid |
| `GET /blog` | Blog post list |
| `GET /blog/:slug` | Individual blog post |
| `GET /api/projects` | JSON API — projects list |
| `GET /api/posts` | JSON API — blog posts list |

## Architecture

```
portfolio/
├── models/      — Domain types (Project, BlogPost, Profile)  [zio-json codecs]
├── services/    — PortfolioService algebra + in-memory Live impl
├── views/       — Server-side HTML with ScalaTags DSL
├── routes/      — ZIO HTTP route definitions
└── Main.scala   — ZIO app entry point
```

### Key design choices

- **ZIO HTTP** for routing and static file serving
- **ScalaTags** for type-safe server-side HTML (no template strings, no XSS)
- **ZIO JSON** for codec derivation and JSON API endpoints
- **ZLayer** for dependency injection (swap `PortfolioServiceLive` for a DB-backed version with zero change to routes/views)

## Getting Started

### Prerequisites

- JDK 17 or 21
- SBT 1.10+

### Run in dev mode (auto-reload)

```bash
sbt dev        # uses sbt-revolver, reloads on code change
```

### Run normally

```bash
sbt run
```

Open [http://localhost:8080](http://localhost:8080)

### Build fat JAR

```bash
sbt assembly
java -jar target/scala-3.4.2/portfolio-assembly-0.1.0.jar
```

### Docker

```bash
sbt assembly
docker build -t portfolio .
docker run -p 8080:8080 portfolio
```

## Extending the Project

### Add a database

Replace `PortfolioServiceLive.layer` with a ZLayer backed by **ZIO Quill** or **Skunk**:

```scala
// services/DbPortfolioService.scala
val layer: ZLayer[DataSource, Nothing, PortfolioService] = ...
```

Wire it in `Main.scala`:

```scala
Server.serve(Routes.routes)
  .provide(
    Server.defaultWithPort(8080),
    DbPortfolioService.layer,
    DataSourceLive.layer,     // Hikari / PG datasource
  )
```

### Add Markdown rendering

Add `flexmark-java` to `build.sbt` and convert `BlogPost.content` from Markdown to HTML in the service layer before passing to views.

### Deploy to Fly.io

```bash
fly launch --dockerfile Dockerfile
fly deploy
```

## Customise

Edit `PortfolioServiceLive.scala` to update your profile, projects, and blog posts. All content lives in one file for simplicity.
