package com.calcul.db

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
      jdbcUrl = sys.env.getOrElse("DATABASE_URL", "jdbc:postgresql://localhost:5432/calcul"),
      username = sys.env.getOrElse("DATABASE_USER", "postgres"),
      password = sys.env.getOrElse("DATABASE_PASSWORD", "postgres"),
      maximumPoolSize = sys.env.get("DATABASE_POOL_SIZE").flatMap(_.toIntOption).getOrElse(10)
    )

  def createDataSource(config: DatabaseConfig): HikariDataSource =
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password)
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.setAutoCommit(true)
    new HikariDataSource(hikariConfig)
