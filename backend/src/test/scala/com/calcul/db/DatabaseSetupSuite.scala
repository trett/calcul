package com.calcul.db

import munit.FunSuite
import java.sql.DriverManager

class DatabaseSetupSuite extends FunSuite:

  test("TestDbInit executes schema.sql and creates all tables") {
    val jdbcUrl = "jdbc:h2:mem:test_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    val conn    = DriverManager.getConnection(jdbcUrl, "sa", "")
    try
      val tablesCreated = TestDbInit.initSchema(conn)
      assert(tablesCreated.contains("users"), "users table should be created")
      assert(tablesCreated.contains("daily_targets"), "daily_targets table should be created")
      assert(tablesCreated.contains("meals"), "meals table should be created")
      assert(tablesCreated.contains("meal_items"), "meal_items table should be created")
      assert(tablesCreated.contains("daily_weights"), "daily_weights table should be created")
    finally conn.close()
  }

  test("DatabaseConfig creates a valid HikariDataSource configuration") {
    val config = DatabaseConfig(
      jdbcUrl = "jdbc:h2:mem:config_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      username = "sa",
      password = "",
      maximumPoolSize = 5
    )
    val ds = DatabaseConfig.createDataSource(config)
    try
      assertEquals(ds.getMaximumPoolSize, 5)
      val conn = ds.getConnection
      try
        assert(!conn.isClosed, "Connection should be open")
      finally
        conn.close()
    finally ds.close()
  }
