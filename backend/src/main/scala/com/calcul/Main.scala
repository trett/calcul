package com.calcul

import org.slf4j.LoggerFactory
import ox.*
import com.calcul.ai.GeminiService
import com.calcul.auth.AuthConfig
import com.calcul.db.DatabaseConfig
import com.calcul.server.ServerRoutes

object Main:

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit =
    val dbConfig   = DatabaseConfig.fromEnv()
    val dataSource = DatabaseConfig.createDataSource(dbConfig)

    val geminiService = new GeminiService()
    val authConfig    = AuthConfig.fromEnv()

    val host = sys.env.getOrElse("HOST", "0.0.0.0")
    val port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)

    val routes = new ServerRoutes(dataSource, geminiService, authConfig)
    val server = routes.createServer(host, port)

    sys.addShutdownHook {
      logger.info("Shutting down CalTrack AI and closing connection pool...")
      dataSource.close()
    }

    logger.info(s"Starting CalTrack AI Netty server on $host:$port...")
    supervised {
      val binding = server.start()
      logger.info(s"CalTrack AI server running at http://${binding.hostName}:${binding.port}")
      Thread.currentThread().join()
    }
