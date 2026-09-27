package ru.trett.calcul.db

import com.augustnagro.magnum.*
import org.slf4j.LoggerFactory

import java.sql.Connection
import javax.sql.DataSource
import scala.util.{Failure, Success, Try}

trait DbTransactor:
  def withConnection[T](f: DbCon ?=> T): T
  def withTransaction[T](f: DbTx ?=> T): Either[String, T]

object DbTransactor:

  private val logger = LoggerFactory.getLogger(getClass)

  def fromDataSource(ds: DataSource): DbTransactor =
    fromTransactor(Transactor(ds))

  def fromTransactor(xa: Transactor): DbTransactor = new DbTransactor:
    def withConnection[T](f: DbCon ?=> T): T = connect(xa)(f)
    def withTransaction[T](f: DbTx ?=> T): Either[String, T] =
      Try(transact(xa)(f)) match
        case Success(res) => Right(res)
        case Failure(ex) =>
          val msg = Option(ex.getMessage).getOrElse(ex.toString)
          logger.error(s"Transaction failed: $msg", ex)
          Left(msg)

  def fromConnection(conn: Connection): DbTransactor = new DbTransactor:
    def withConnection[T](f: DbCon ?=> T): T =
      f(using MagnumBridge.dbCon(conn))

    def withTransaction[T](f: DbTx ?=> T): Either[String, T] =
      val oldAutoCommit = conn.getAutoCommit
      conn.setAutoCommit(false)
      Try(f(using MagnumBridge.dbTx(conn))) match
        case Success(result) =>
          conn.commit()
          conn.setAutoCommit(oldAutoCommit)
          Right(result)
        case Failure(ex) =>
          conn.rollback()
          conn.setAutoCommit(oldAutoCommit)
          val msg = Option(ex.getMessage).getOrElse(ex.toString)
          logger.error(s"Transaction failed: $msg", ex)
          Left(msg)
