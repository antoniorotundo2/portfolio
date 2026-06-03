val zioVersion       = "2.1.26"
val zioHttpVersion   = "3.11.2"
val zioJsonVersion   = "0.7.3"
val scalatagsVersion = "0.13.1"

// Scala 3.3.x LTS release
ThisBuild / scalaVersion := "3.3.7"
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
      
      // ZIO JSON (senza derives — useremo given espliciti)
      "dev.zio" %% "zio-json"    % zioJsonVersion,
      
      // ScalaTags for HTML DSL
      "com.lihaoyi" %% "scalatags" % scalatagsVersion,
      
      // Markdown rendering
      "org.commonmark" % "commonmark"                    % "0.28.0",
      "org.commonmark" % "commonmark-ext-gfm-tables"     % "0.28.0",
      "org.commonmark" % "commonmark-ext-gfm-strikethrough" % "0.28.0",
      
      // YAML front matter parsing
      "org.yaml" % "snakeyaml" % "2.6",
      
      // Logging
      "dev.zio" %% "zio-logging"              % "2.5.3",
      "dev.zio" %% "zio-logging-slf4j-bridge" % "2.5.3",
      "ch.qos.logback" % "logback-classic"    % "1.5.34",
      
      // Email sending (Jakarta Mail / Angus Mail per OTP)
      "org.eclipse.angus" % "angus-mail" % "2.1.0-M1",
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Ykind-projector",
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