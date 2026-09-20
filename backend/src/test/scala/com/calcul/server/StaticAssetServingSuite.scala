package com.calcul.server

import munit.FunSuite
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import ox.*
import com.calcul.db.TestPostgresContainer

class StaticAssetServingSuite extends FunSuite:

  test("ServerRoutes serves index.html on root path GET /") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      val server = routes.createServer(port = 8899)

      supervised {
        val binding = server.start()
        try
          val client = HttpClient.newHttpClient()
          val request = HttpRequest
            .newBuilder()
            .uri(URI.create("http://localhost:8899/"))
            .GET()
            .build()

          val response = client.send(request, HttpResponse.BodyHandlers.ofString())
          assertEquals(response.statusCode(), 200)
          assert(response.body().contains("CalTrack"))
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
          val client = HttpClient.newHttpClient()
          val request = HttpRequest
            .newBuilder()
            .uri(URI.create("http://localhost:8898/assets/main.js"))
            .GET()
            .build()

          val response = client.send(request, HttpResponse.BodyHandlers.ofString())
          assertEquals(response.statusCode(), 200)
          assertEquals(response.headers().firstValue("Content-Type").orElse(""), "application/javascript")
        finally binding.stop()
      }
    finally conn.close()
  }
