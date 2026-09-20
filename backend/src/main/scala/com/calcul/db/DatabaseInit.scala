package com.calcul.db

import java.io.InputStream
import java.sql.Connection
import scala.io.Source
import scala.util.Using

object DatabaseInit:

  def initSchema(conn: Connection): Set[String] =
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

    val md = conn.getMetaData
    Using.resource(md.getTables(null, null, "%", Array("TABLE"))) { rs =>
      val tableNames = collection.mutable.Set[String]()
      while rs.next() do tableNames.add(rs.getString("TABLE_NAME").toLowerCase)
      tableNames.toSet
    }
