package ru.trett.calcul.db

import munit.FunSuite

class DatabaseSetupSuite extends FunSuite:

  test("TestDbInit executes schema.sql and creates all tables") {
    val conn = TestPostgresContainer.dataSource.getConnection
    try
      val initResult = TestDbInit.initSchema(conn)
      assert(initResult.isRight, s"Schema init failed: $initResult")
      val tablesCreated = initResult.getOrElse(Set.empty)
      assert(tablesCreated.contains("users"), "users table should be created")
      assert(tablesCreated.contains("daily_targets"), "daily_targets table should be created")
      assert(tablesCreated.contains("meals"), "meals table should be created")
      assert(tablesCreated.contains("meal_items"), "meal_items table should be created")
      assert(tablesCreated.contains("daily_weights"), "daily_weights table should be created")
    finally conn.close()
  }

  test("users table contains encrypted_gemini_api_key column") {
    val conn = TestPostgresContainer.dataSource.getConnection
    try
      val initResult = TestDbInit.initSchema(conn)
      assert(initResult.isRight, s"Schema init failed: $initResult")
      val nullStr = Option.empty[String].orNull
      val rs      = conn.getMetaData.getColumns(nullStr, nullStr, "users", "encrypted_gemini_api_key")
      assert(rs.next(), "users table should contain encrypted_gemini_api_key column")
    finally conn.close()
  }

  test("DatabaseConfig creates a valid HikariDataSource configuration") {
    val config = DatabaseConfig(
      jdbcUrl = TestPostgresContainer.jdbcUrl,
      username = TestPostgresContainer.username,
      password = TestPostgresContainer.password,
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
