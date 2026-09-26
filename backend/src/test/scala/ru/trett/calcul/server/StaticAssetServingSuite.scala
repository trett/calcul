package ru.trett.calcul.server

import munit.FunSuite
import ox.*
import sttp.client4.quick.*
import ru.trett.calcul.db.TestPostgresContainer

class StaticAssetServingSuite extends FunSuite:

  test("ServerRoutes serves index.html on root path GET /") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      val server = routes.createServer(port = 8899)

      supervised {
        val binding = server.start()
        try
          val response = quickRequest.get(uri"http://localhost:8899/").send()
          assertEquals(response.code.code, 200)
          assert(response.body.contains("CalTrack"))
        finally binding.stop()
      }
    finally conn.close()
  }

  test("ServerRoutes serves assets on GET /assets/*") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      val server = routes.createServer(port = 8898)

      supervised {
        val binding = server.start()
        try
          val response = quickRequest.get(uri"http://localhost:8898/assets/main.js").send()
          assertEquals(response.code.code, 200)
          assertEquals(response.header("Content-Type").getOrElse(""), "application/javascript")
        finally binding.stop()
      }
    finally conn.close()
  }
