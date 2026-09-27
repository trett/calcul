package ru.trett.calcul.db

import com.augustnagro.magnum.*

import java.sql.Connection
import javax.sql.DataSource

trait DbTransactor:
  def withConnection[T](f: DbCon ?=> T): T
  def withTransaction[T](f: DbTx ?=> T): T

object DbTransactor:

  def fromDataSource(ds: DataSource): DbTransactor =
    fromTransactor(Transactor(ds))

  def fromTransactor(xa: Transactor): DbTransactor = new DbTransactor:
    def withConnection[T](f: DbCon ?=> T): T = connect(xa)(f)
    def withTransaction[T](f: DbTx ?=> T): T = transact(xa)(f)

  def fromConnection(conn: Connection): DbTransactor = new DbTransactor:
    def withConnection[T](f: DbCon ?=> T): T =
      f(using MagnumBridge.dbCon(conn))

    def withTransaction[T](f: DbTx ?=> T): T =
      val oldAutoCommit = conn.getAutoCommit
      conn.setAutoCommit(false)
      try
        val result = f(using MagnumBridge.dbTx(conn))
        conn.commit()
        result
      catch
        case ex: Throwable =>
          conn.rollback()
          throw ex
      finally conn.setAutoCommit(oldAutoCommit)
