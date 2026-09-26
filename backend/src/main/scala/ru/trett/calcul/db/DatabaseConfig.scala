package ru.trett.calcul.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

final case class DatabaseConfig(
    jdbcUrl: String,
    username: String,
    password: String,
    maximumPoolSize: Int = 10
)

object DatabaseConfig:
  def fromEnv(): DatabaseConfig =
    DatabaseConfig(
      jdbcUrl = sys.env
        .get("DATABASE_URL")
        .orElse(sys.env.get("JDBC_URL"))
        .getOrElse("jdbc:postgresql://localhost:5432/calcul"),
      username = sys.env.get("DATABASE_USER").orElse(sys.env.get("DB_USER")).getOrElse("postgres"),
      password = sys.env.get("DATABASE_PASSWORD").orElse(sys.env.get("DB_PASSWORD")).getOrElse("postgres"),
      maximumPoolSize =
        sys.env.get("DATABASE_POOL_SIZE").orElse(sys.env.get("DB_POOL_SIZE")).flatMap(_.toIntOption).getOrElse(10)
    )

  def createDataSource(config: DatabaseConfig): HikariDataSource =
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password)
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.setAutoCommit(true)
    new HikariDataSource(hikariConfig)
