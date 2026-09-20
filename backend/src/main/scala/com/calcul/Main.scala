package com.calcul

import ox.*
import com.calcul.ai.GeminiService
import com.calcul.auth.AuthConfig
import com.calcul.db.{DatabaseConfig, DatabaseInit}
import com.calcul.server.ServerRoutes

object Main:

  def main(args: Array[String]): Unit =
    val dbConfig   = DatabaseConfig.fromEnv()
    val dataSource = DatabaseConfig.createDataSource(dbConfig)

    val conn = dataSource.getConnection
    try
      DatabaseInit.initSchema(conn)
    finally conn.close()

    val geminiApiKey  = sys.env.get("GEMINI_API_KEY")
    val geminiService = new GeminiService(geminiApiKey)
    val authConfig    = AuthConfig.fromEnv()

    val host = sys.env.getOrElse("HOST", "0.0.0.0")
    val port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)

    val serverConn = dataSource.getConnection
    val routes     = new ServerRoutes(serverConn, geminiService, authConfig)
    val server     = routes.createServer(host, port)

    println(s"Starting CalTrack AI Netty server on $host:$port...")
    supervised {
      val binding = server.start()
      println(s"CalTrack AI server running at http://${binding.hostName}:${binding.port}")
      Thread.currentThread().join()
    }
