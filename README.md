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
java -jar target/scala-3.3.7/portfolio-assembly-0.1.0.jar
```

### Run tests / formatting

```bash
sbt test            # ZIO Test suites
sbt scalafmtCheckAll # verifica formattazione (come in CI)
```

CI (GitHub Actions, `.github/workflows/ci.yml`) esegue `scalafmtCheckAll` e `test` su ogni push/PR verso `main`.

### Docker

```bash
sbt assembly
docker build -t portfolio .
docker run -p 8080:8080 portfolio
```

## Environment variables

L'app pubblica parte senza configurazione. Le variabili servono per l'area admin
(salvataggio su GitHub + OTP via email) e per la SEO assoluta.

**Admin (richieste per usare `/admin`):**

| Variabile        | Default              | Descrizione                                  |
|------------------|----------------------|----------------------------------------------|
| `GITHUB_TOKEN`   | — (obbligatoria)     | Token con permesso di scrittura sul repo     |
| `SMTP_PASSWORD`  | — (obbligatoria)     | API key Resend per l'invio dell'OTP          |
| `ADMIN_EMAIL`    | `admin@example.com`  | Email che riceve il codice OTP               |
| `GITHUB_OWNER`   | `your-username`      | Owner del repo dei contenuti                 |
| `GITHUB_REPO`    | `portfolio`          | Nome del repo                                |
| `GITHUB_BRANCH`  | `main`               | Branch su cui committare                     |
| `CONTENT_BASE_PATH` | `src/main/resources` | Prefisso path dei contenuti nel repo      |
| `SMTP_FROM`      | `onboarding@resend.dev` | Mittente dell'email OTP                   |

**Sito / sicurezza (opzionali):**

| Variabile      | Default | Descrizione                                                  |
|----------------|---------|--------------------------------------------------------------|
| `COOKIE_SECURE`| `true`  | Cookie di sessione solo su HTTPS (metti `false` in dev HTTP) |
| `SITE_URL`     | —       | Origin pubblico per `canonical`/`og:url` (es. `https://...`) |
| `OG_IMAGE_URL` | —       | URL assoluto dell'immagine Open Graph per le anteprime       |

> Nota: `GITHUB_TOKEN`/`SMTP_PASSWORD` sono valutate in modo lazy — l'app pubblica e i
> test girano senza, ma l'area admin fallisce in modo esplicito se mancano.

## Endpoints

| Path             | Descrizione                                  |
|------------------|----------------------------------------------|
| `/`              | Home                                         |
| `/projects`      | Elenco progetti                              |
| `/blog`, `/blog/:slug` | Blog e singolo articolo                |
| `/api/projects`, `/api/posts` | API JSON                        |
| `/healthz`       | Health check (per Render)                    |
| `/robots.txt`, `/sitemap.xml` | SEO                             |
| `/admin`         | Area amministratore (OTP + editor contenuti) |

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

### Deploy

Il progetto è pensato per il deploy via Docker (vedi `Dockerfile`). Su **Render**:
crea un *Web Service* dal repo, usa il Dockerfile, imposta le variabili d'ambiente
(vedi sopra) e configura l'health check su `/healthz`. Ogni push su `main` innesca un redeploy.

## Customise

I contenuti sono **file Markdown con frontmatter YAML** in `src/main/resources/`
(`home/`, `layout/`, `projects/`, `blog/`, `notfound/`), caricati all'avvio dai loader
in `services/PortfolioService.scala`. Puoi modificarli direttamente nel repo oppure
dall'area `/admin`, che committa su GitHub (con validazione del contenuto prima del commit).
