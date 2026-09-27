package ru.trett.calcul.db

import com.augustnagro.magnum.{DbCon, DbTx, SqlLogger, Transactor, connect}
import org.slf4j.LoggerFactory

import javax.sql.DataSource
import scala.concurrent.duration.*
import scala.util.NotGiven
import scala.util.control.{NoStackTrace, NonFatal}

class DB(val dataSource: DataSource) extends AutoCloseable:

  private val logger = LoggerFactory.getLogger(getClass)

  private val transactor = Transactor(
    dataSource = dataSource,
    sqlLogger = SqlLogger.logSlowQueries(200.millis)
  )

  /** Runs `f` in a connection context. */
  def withConnection[T](f: DbCon ?=> T): T =
    connect(transactor)(f)

  /** Runs `f` in a transaction. The transaction is committed if the result is a Right, and rolled back otherwise. */
  def transactEither[E, T](f: DbTx ?=> Either[E, T]): Either[E, T] =
    try
      com.augustnagro.magnum.transact(transactor)(
        Right(f.fold(e => throw DB.LeftException(e), identity)) // scalafix:ok DisableSyntax.throw
      )
    catch case e: DB.LeftException[E] @unchecked => Left(e.left)

  /** Runs `f` in a transaction. The result cannot be an `Either`, as then [[transactEither]] should be used. The
    * transaction is committed if no exception is thrown.
    */
  def transact[T](f: DbTx ?=> T)(using NotGiven[T <:< Either[?, ?]]): T =
    com.augustnagro.magnum.transact(transactor)(f)

  /** Helper that runs a transaction and wraps non-fatal errors in Either[String, T]. */
  def withTransaction[T](f: DbTx ?=> T): Either[String, T] =
    try Right(com.augustnagro.magnum.transact(transactor)(f))
    catch
      case NonFatal(ex) =>
        val msg = Option(ex.getMessage).getOrElse(ex.toString)
        logger.error(s"Transaction failed: $msg", ex)
        Left(msg)

  override def close(): Unit =
    dataSource match
      case c: AutoCloseable => c.close()
      case _                => ()

end DB

object DB:
  private class LeftException[E](val left: E) extends RuntimeException with NoStackTrace
