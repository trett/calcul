val scala3Version = "3.9.0"

ThisBuild / scalaVersion := scala3Version
ThisBuild / organization := "com.calcul"
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Werror",
  "-Wunused:all"
)

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

val oxVersion = "1.0.7"
val tapirVersion = "1.13.31"
val sttpAiVersion = "0.11.0"
val upickleVersion = "4.4.3"
val laminarVersion = "17.2.1"
val laminarShoelaceVersion = "0.2.0"
val postgresqlVersion = "42.7.5"
val hikariVersion = "6.2.0"
val munitVersion = "1.1.0"

lazy val root = (project in file("."))
  .aggregate(shared.jvm, shared.js, backend, frontend)
  .settings(
    name := "calcul-root",
    publish / skip := true
  )

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))
  .settings(
    name := "calcul-shared",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % upickleVersion,
      "com.softwaremill.sttp.tapir" %%% "tapir-core" % tapirVersion,
      "com.softwaremill.sttp.tapir" %%% "tapir-json-upickle" % tapirVersion,
      "org.scalameta" %%% "munit" % munitVersion % Test
    )
  )

lazy val backend = (project in file("backend"))
  .enablePlugins(GraalVMNativeImagePlugin, JavaAppPackaging)
  .dependsOn(shared.jvm)
  .settings(
    name := "calcul-backend",
    Compile / mainClass := Some("com.calcul.Main"),
    graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      "-H:+ReportExceptionStackTraces",
      "--enable-http",
      "--enable-https",
      "--install-exit-handlers",
      "-H:IncludeResources=.*schema\\.sql$",
      "-H:IncludeResources=.*webapp/.*"
    ),
    libraryDependencies ++= Seq(
      "com.softwaremill.ox" %% "core" % oxVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % tapirVersion,
      "com.softwaremill.sttp.ai" %% "gemini" % sttpAiVersion,
      "org.postgresql" % "postgresql" % postgresqlVersion,
      "com.zaxxer" % "HikariCP" % hikariVersion,
      "com.h2database" % "h2" % "2.3.232" % Test,
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )

lazy val frontend = (project in file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared.js)
  .settings(
    name := "calcul-frontend",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % laminarVersion,
      "com.raquo" %%% "laminar-shoelace" % laminarShoelaceVersion,
      "org.scalameta" %%% "munit" % munitVersion % Test
    )
  )
