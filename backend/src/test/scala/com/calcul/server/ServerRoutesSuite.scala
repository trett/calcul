package com.calcul.server

import munit.FunSuite
import java.sql.{Connection, DriverManager}
import com.calcul.db.DatabaseInit

class ServerRoutesSuite extends FunSuite:

  test("ServerRoutes initializes routes and NettySyncServer") {
    val jdbcUrl          = "jdbc:h2:mem:server_routes_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    val conn: Connection = DriverManager.getConnection(jdbcUrl, "sa", "")
    try
      DatabaseInit.initSchema(conn)
      val routes = new ServerRoutes(conn)
      assertEquals(routes.allRoutes.size, 14)
      val server = routes.createServer(port = 8089)
      assert(server != null, "Server should be initialized")
    finally conn.close()
  }
