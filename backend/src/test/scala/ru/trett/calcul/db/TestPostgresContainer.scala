package ru.trett.calcul.db

import org.testcontainers.containers.PostgreSQLContainer

import java.sql.{Connection, DriverManager}
import javax.sql.DataSource
import scala.util.Using

object TestPostgresContainer:

  private lazy val container: PostgreSQLContainer[Nothing] =
    val c = new PostgreSQLContainer("postgres:16-alpine")
    c.start()
    Using.resource(DriverManager.getConnection(c.getJdbcUrl, c.getUsername, c.getPassword)) { conn =>
      TestDbInit.initSchema(conn) match
        case Left(err) => System.err.println(s"Failed to initialize test schema: $err")
        case Right(_)  => ()
    }
    c

  def jdbcUrl: String  = container.getJdbcUrl
  def username: String = container.getUsername
  def password: String = container.getPassword

  lazy val dataSource: DataSource =
    DatabaseConfig.createDataSource(
      DatabaseConfig(
        jdbcUrl = jdbcUrl,
        username = username,
        password = password,
        maximumPoolSize = 5
      )
    )

  lazy val db: DB = DB(dataSource)

  def newConnection(): Connection =
    dataSource.getConnection

  def clearData(): Unit =
    Using.resource(dataSource.getConnection) { conn =>
      Using.resource(conn.createStatement()) { stmt =>
        stmt.execute("TRUNCATE TABLE meal_items, meals, daily_targets, daily_weights, users CASCADE;")
      }
    }
