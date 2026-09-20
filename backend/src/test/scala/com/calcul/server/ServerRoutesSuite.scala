package com.calcul.server

import munit.FunSuite
import com.calcul.db.TestPostgresContainer

class ServerRoutesSuite extends FunSuite:

  test("ServerRoutes initializes routes and NettySyncServer") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      assertEquals(routes.allRoutes.size, 15)
      val server = routes.createServer(port = 8089)
      assert(Option(server).isDefined, "Server should be initialized")
    finally conn.close()
  }
