package ru.trett.calcul.server

import munit.FunSuite
import ox.*
import ru.trett.calcul.db.TestPostgresContainer
import sttp.client4.quick.*

class StaticAssetServingSuite extends FunSuite:

  test("ServerRoutes serves index.html on root path GET /") {
    val routes = new ServerRoutes(TestPostgresContainer.db)
    val server = routes.createServer(port = 8899)

    supervised {
      val binding = server.start()
      try
        val response = quickRequest.get(uri"http://localhost:8899/").send()
        assertEquals(response.code.code, 200)
        assert(response.body.contains("CalTrack"))
      finally binding.stop()
    }
  }

  test("ServerRoutes serves assets on GET /assets/*") {
    val routes = new ServerRoutes(TestPostgresContainer.db)
    val server = routes.createServer(port = 8898)

    supervised {
      val binding = server.start()
      try
        val response = quickRequest.get(uri"http://localhost:8898/assets/main.js").send()
        assertEquals(response.code.code, 200)
        assertEquals(response.header("Content-Type").getOrElse(""), "application/javascript")
      finally binding.stop()
    }
  }
