package ru.trett.calcul.db

import java.sql.Connection
import javax.sql.DataSource
import scala.util.Using

trait DbTransactor:
  def withConnection[T](f: Connection => T): T
  def withTransaction[T](f: Connection => T): T

object DbTransactor:

  def fromDataSource(ds: DataSource): DbTransactor = new DbTransactor:
    def withConnection[T](f: Connection => T): T =
      Using.resource(ds.getConnection)(f)

    def withTransaction[T](f: Connection => T): T =
      Using.resource(ds.getConnection) { conn =>
        val oldAutoCommit = conn.getAutoCommit
        conn.setAutoCommit(false)
        try
          val result = f(conn)
          conn.commit()
          result
        catch
          case ex: Throwable =>
            conn.rollback()
            throw ex
        finally conn.setAutoCommit(oldAutoCommit)
      }

  def fromConnection(conn: Connection): DbTransactor = new DbTransactor:
    def withConnection[T](f: Connection => T): T = f(conn)

    def withTransaction[T](f: Connection => T): T =
      val oldAutoCommit = conn.getAutoCommit
      conn.setAutoCommit(false)
      try
        val result = f(conn)
        conn.commit()
        result
      catch
        case ex: Throwable =>
          conn.rollback()
          throw ex
      finally conn.setAutoCommit(oldAutoCommit)
