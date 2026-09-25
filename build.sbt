import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.*
import com.typesafe.sbt.packager.docker.DockerApiVersion

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
val slf4jVersion = "2.0.17"
val munitVersion = "1.1.0"

lazy val buildImage = taskKey[Unit]("Build docker image")
lazy val generateFrontendAssets = taskKey[Seq[File]]("Build frontend and copy assets to managed resources")

lazy val root = (project in file("."))
  .aggregate(shared.jvm, shared.js, backend, frontend)
  .settings(
    name := "calcul-root",
    publish / skip := true,
    buildImage := {
      (backend / Docker / publishLocal).value
    }
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
  .enablePlugins(GraalVMNativeImagePlugin, JavaAppPackaging, DockerPlugin)
  .dependsOn(shared.jvm)
  .settings(
    name := "calcul-backend",
    Compile / mainClass := Some("com.calcul.Main"),
    Compile / resourceGenerators += generateFrontendAssets.taskValue,
    Test / resourceGenerators += generateFrontendAssets.taskValue,
    Test / parallelExecution := false,
    // GraalVM Native Image Settings
    graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      "-H:+ReportExceptionStackTraces",
      "--verbose",
      "--enable-http",
      "--enable-https",
      "--install-exit-handlers",
      "-H:IncludeResources=.*schema\\.sql$",
      "-H:IncludeResources=.*webapp/.*",
      "--initialize-at-build-time=org.slf4j",
      "--initialize-at-run-time=io.netty.channel.epoll,io.netty.channel.kqueue,io.netty.channel.unix,io.netty.channel.uring,io.netty.channel.kqueue.KQueueEventArray,io.netty.channel.kqueue.Native,io.netty.channel.kqueue.BsdSocket"
    ),
    // Docker Settings
    Docker / packageName := "calcul-backend",
    Docker / version := sys.env.get("DOCKER_TAG").getOrElse("local"),
    dockerPermissionStrategy := DockerPermissionStrategy.None,
    dockerBaseImage := {
      if (sys.env.get("NATIVE_IMAGE").contains("true")) "debian:12-slim"
      else "eclipse-temurin:21-jre-jammy"
    },
    dockerApiVersion := Some(DockerApiVersion(1, 40)),
    dockerRepository := sys.env.get("REGISTRY"),
    dockerExposedPorts := Seq(8080),
    dockerCommands := {
      val commands = dockerCommands.value
      val filteredCommands = commands.filter {
        case Cmd("RUN", _*) => false
        case Cmd("USER", _*) => false
        case Cmd("ENTRYPOINT", _*) => false
        case Cmd("CMD", _*) => false
        case Cmd("WORKDIR", _*) => false
        case ExecCmd("ENTRYPOINT", _*) => false
        case ExecCmd("CMD", _*) => false
        case _ => true
      }
      val runCmds =
        if (sys.env.get("NATIVE_IMAGE").contains("true"))
          Seq(
            Cmd("RUN", "apt-get update && apt-get install -y ca-certificates curl && rm -rf /var/lib/apt/lists/*"),
            Cmd("WORKDIR", "/opt/docker"),
            ExecCmd("ENTRYPOINT", "/opt/docker/bin/calcul-backend")
          )
        else
          Seq(
            Cmd("RUN", "apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*"),
            Cmd("WORKDIR", "/opt/docker"),
            ExecCmd("ENTRYPOINT", "/opt/docker/bin/calcul-backend")
          )
      filteredCommands ++ runCmds
    },
    Docker / mappings := Def.taskDyn {
      val standardMappings = (Docker / mappings).value
      if (sys.env.get("NATIVE_IMAGE").contains("true")) {
        Def.task {
          val nativeImage = (GraalVMNativeImage / packageBin).value
          standardMappings.filter { case (_, path) =>
            !path.contains("bin/calcul-backend") && !path.contains("lib/")
          } :+ (nativeImage -> "/opt/docker/bin/calcul-backend")
        }
      } else {
        Def.task(standardMappings)
      }
    }.value,
    libraryDependencies ++= Seq(
      "com.softwaremill.ox" %% "core" % oxVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % tapirVersion,
      "com.softwaremill.sttp.ai" %% "gemini" % sttpAiVersion,
      "org.postgresql" % "postgresql" % postgresqlVersion,
      "com.zaxxer" % "HikariCP" % hikariVersion,
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "org.slf4j" % "slf4j-simple" % slf4jVersion,
      "org.testcontainers" % "postgresql" % "1.20.4" % Test,
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

backend / generateFrontendAssets := Def.taskDyn {
  val isProd = sys.env.get("PRODUCTION").contains("true") || sys.env.get("NATIVE_IMAGE").contains("true")
  if (isProd) {
    Def.task {
      val _              = (frontend / Compile / fullLinkJS).value
      val crossTargetDir = (frontend / Compile / fullLinkJS / crossTarget).value
      val jsFile         = crossTargetDir / "calcul-frontend-opt" / "main.js"
      val targetDir      = (backend / Compile / resourceManaged).value / "webapp" / "assets"
      IO.createDirectory(targetDir)
      if (jsFile.exists()) {
        val destJs = targetDir / "main.js"
        IO.copyFile(jsFile, destJs)
        Seq(destJs)
      } else Seq.empty[File]
    }
  } else {
    Def.task {
      val _              = (frontend / Compile / fastLinkJS).value
      val crossTargetDir = (frontend / Compile / fastLinkJS / crossTarget).value
      val jsDir          = crossTargetDir / "calcul-frontend-fastopt"
      val jsFile         = jsDir / "main.js"
      val jsMapFile      = jsDir / "main.js.map"
      val targetDir      = (backend / Compile / resourceManaged).value / "webapp" / "assets"
      IO.createDirectory(targetDir)
      var copied = Seq.empty[File]
      if (jsFile.exists()) {
        val destJs = targetDir / "main.js"
        IO.copyFile(jsFile, destJs)
        copied = copied :+ destJs
      }
      if (jsMapFile.exists()) {
        val destMap = targetDir / "main.js.map"
        IO.copyFile(jsMapFile, destMap)
        copied = copied :+ destMap
      }
      copied
    }
  }
}.value
