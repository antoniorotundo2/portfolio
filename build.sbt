val zioVersion       = "2.1.9"
val zioHttpVersion   = "3.0.1"
val zioJsonVersion   = "0.7.3"
val scalatagsVersion = "0.13.1"
val flexmarkVersion  = "0.64.8"

ThisBuild / scalaVersion := "3.4.2"
ThisBuild / organization := "dev.portfolio"

lazy val root = (project in file("."))
  .settings(
    name    := "portfolio",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      // ZIO Core
      "dev.zio" %% "zio"         % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      // ZIO HTTP
      "dev.zio" %% "zio-http"    % zioHttpVersion,
      // ZIO JSON
      "dev.zio" %% "zio-json"    % zioJsonVersion,
      // ScalaTags for HTML DSL
      "com.lihaoyi" %% "scalatags" % scalatagsVersion,
      // Markdown rendering
      "com.vladsch.flexmark" % "flexmark"               % flexmarkVersion,
      "com.vladsch.flexmark" % "flexmark-ext-tables"    % flexmarkVersion,
      "com.vladsch.flexmark" % "flexmark-ext-gfm-strikethrough" % flexmarkVersion,
      "com.vladsch.flexmark" % "flexmark-ext-yaml-front-matter" % flexmarkVersion,
      // Logging
      "dev.zio" %% "zio-logging"              % "2.3.0",
      "dev.zio" %% "zio-logging-slf4j-bridge" % "2.3.0",
      "ch.qos.logback" % "logback-classic"    % "1.5.8",
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
    ),
    Compile / mainClass := Some("portfolio.Main"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case "reference.conf"                     => MergeStrategy.concat
      case _                                    => MergeStrategy.first
    },
  )

addCommandAlias("dev", "~reStart")
