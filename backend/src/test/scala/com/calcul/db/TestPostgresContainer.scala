package com.calcul.db

import org.testcontainers.containers.PostgreSQLContainer
import java.sql.{Connection, DriverManager}
import scala.util.Using

object TestPostgresContainer:

  private lazy val container: PostgreSQLContainer[Nothing] =
    val c = new PostgreSQLContainer("postgres:16-alpine")
    c.start()
    Using.resource(DriverManager.getConnection(c.getJdbcUrl, c.getUsername, c.getPassword)) { conn =>
      TestDbInit.initSchema(conn)
    }
    c

  def jdbcUrl: String  = container.getJdbcUrl
  def username: String = container.getUsername
  def password: String = container.getPassword

  def newConnection(): Connection =
    DriverManager.getConnection(jdbcUrl, username, password)

  def clearData(): Unit =
    Using.resource(newConnection()) { conn =>
      Using.resource(conn.createStatement()) { stmt =>
        stmt.execute("TRUNCATE TABLE meal_items, meals, daily_targets, daily_weights, users CASCADE;")
      }
    }
