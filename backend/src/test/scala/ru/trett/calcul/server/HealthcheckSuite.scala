package ru.trett.calcul.server

import munit.FunSuite
import java.io.File
import java.nio.file.Files
import ox.*
import sttp.client4.quick.*
import ru.trett.calcul.db.TestPostgresContainer

class HealthcheckSuite extends FunSuite:

  test("ServerRoutes serves healthcheck endpoint on GET /api/health") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      val server = routes.createServer(port = 8897)

      supervised {
        val binding = server.start()
        try
          val response = quickRequest.get(uri"http://localhost:8897/api/health").send()
          assertEquals(response.code.code, 200)
          assert(response.body.contains("ok"))
        finally binding.stop()
      }
    finally conn.close()
  }

  test("docker-compose.yml exists and specifies postgres schema initialization and calcul-backend image") {
    val compose = new File("docker-compose.yml")
    assert(compose.exists(), "docker-compose.yml must exist")

    val composeContent = Files.readString(compose.toPath)
    assert(composeContent.contains("schema.sql"), "docker-compose.yml must mount schema.sql")
    assert(composeContent.contains("postgres"), "docker-compose.yml must define postgres service")
    assert(composeContent.contains("calcul-backend"), "docker-compose.yml must reference calcul-backend image")
  }
