package com.calcul.server

import munit.FunSuite
import java.io.File
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files
import java.sql.{Connection, DriverManager}
import ox.*
import com.calcul.db.DatabaseInit

class HealthcheckSuite extends FunSuite:

  test("ServerRoutes serves healthcheck endpoint on GET /api/health") {
    val jdbcUrl          = "jdbc:h2:mem:healthcheck_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    val conn: Connection = DriverManager.getConnection(jdbcUrl, "sa", "")
    try
      DatabaseInit.initSchema(conn)
      val routes = new ServerRoutes(conn)
      val server = routes.createServer(port = 8897)

      supervised {
        val binding = server.start()
        try
          val client = HttpClient.newHttpClient()
          val request = HttpRequest
            .newBuilder()
            .uri(URI.create("http://localhost:8897/api/health"))
            .GET()
            .build()

          val response = client.send(request, HttpResponse.BodyHandlers.ofString())
          assertEquals(response.statusCode(), 200)
          assert(response.body().contains("ok"))
        finally binding.stop()
      }
    finally conn.close()
  }

  test("Dockerfile and docker-compose.yml exist and specify postgres schema initialization") {
    val dockerfile = new File("Dockerfile")
    val compose    = new File("docker-compose.yml")
    assert(dockerfile.exists(), "Dockerfile must exist")
    assert(compose.exists(), "docker-compose.yml must exist")

    val composeContent = Files.readString(compose.toPath)
    assert(composeContent.contains("schema.sql"), "docker-compose.yml must mount schema.sql")
    assert(composeContent.contains("postgres"), "docker-compose.yml must define postgres service")
  }
