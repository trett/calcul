package com.calcul.db

import java.io.InputStream
import java.sql.Connection
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}

object DatabaseInit:

  private val logger = LoggerFactory.getLogger(getClass)

  def initSchema(ds: DataSource): Set[String] =
    Using.resource(ds.getConnection) { conn =>
      initSchema(conn)
    }

  def initSchema(conn: Connection): Set[String] =
    logger.info("Verifying and applying database schema migrations...")
    Try {
      val schemaResource: InputStream =
        Option(getClass.getResourceAsStream("/schema.sql"))
          .getOrElse(throw new IllegalStateException("Could not find /schema.sql in resources"))

      val sqlScript = Using.resource(Source.fromInputStream(schemaResource, "UTF-8"))(_.mkString)

      Using.resource(conn.createStatement()) { stmt =>
        val statements = sqlScript
          .split(";")
          .map(_.trim)
          .filter(_.nonEmpty)

        for sql <- statements do stmt.execute(sql)
      }

      val md      = conn.getMetaData
      val nullStr = Option.empty[String].orNull
      Using.resource(md.getTables(nullStr, nullStr, "%", Array("TABLE"))) { rs =>
        val tableNames = collection.mutable.Set[String]()
        while rs.next() do tableNames.add(rs.getString("TABLE_NAME").toLowerCase)
        tableNames.toSet
      }
    } match
      case Success(tables) =>
        logger.info(s"Database schema verified/migrated successfully. Tables: ${tables.mkString(", ")}")
        tables
      case Failure(ex) =>
        logger.error("Failed to initialize or migrate database schema", ex)
        throw ex
