package ru.trett.calcul.db

import java.io.InputStream
import java.sql.Connection
import scala.collection.mutable
import scala.io.Source
import scala.util.Using

object TestDbInit:

  def initSchema(conn: Connection): Either[String, Set[String]] =
    val schemaResourceOpt: Option[InputStream] =
      Option(getClass.getResourceAsStream("/schema.sql"))

    schemaResourceOpt match
      case None =>
        Left("Could not find /schema.sql in resources")
      case Some(schemaResource) =>
        Using(Source.fromInputStream(schemaResource, "UTF-8"))(_.mkString).toEither.left.map(_.getMessage).flatMap {
          sqlScript =>
            Using(conn.createStatement()) { stmt =>
              val statements = sqlScript
                .split(";")
                .map(_.trim)
                .filter(_.nonEmpty)

              for sql <- statements do stmt.execute(sql)
            }.toEither.left.map(_.getMessage).flatMap { _ =>
              val md      = conn.getMetaData
              val nullStr = Option.empty[String].orNull
              Using(md.getTables(nullStr, nullStr, "%", Array("TABLE"))) { rs =>
                val tableNames = mutable.Set[String]()
                while rs.next() do tableNames.add(rs.getString("TABLE_NAME").toLowerCase)
                tableNames.toSet
              }.toEither.left.map(_.getMessage)
            }
        }
