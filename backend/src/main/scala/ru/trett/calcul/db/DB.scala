package ru.trett.calcul.db

import com.augustnagro.magnum.{DbCon, DbTx, SqlLogger, Transactor, connect, transact as magnumTransact}
import org.slf4j.LoggerFactory

import javax.sql.DataSource
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

class DB(val dataSource: DataSource) extends AutoCloseable:

  private val logger = LoggerFactory.getLogger(getClass)

  private val transactor = Transactor(
    dataSource = dataSource,
    sqlLogger = SqlLogger.logSlowQueries(200.millis)
  )

  /** Runs `f` in a connection context. */
  def withConnection[T](f: DbCon ?=> T): T =
    connect(transactor)(f)

  /** Runs `f` in a transaction. The transaction is committed if no exception is thrown. */
  def transact[T](f: DbTx ?=> T): T =
    magnumTransact(transactor)(f)

  /** Helper that runs a transaction and wraps non-fatal errors in Either[String, T]. */
  def withTransaction[T](f: DbTx ?=> T): Either[String, T] =
    Try(magnumTransact(transactor)(f)) match
      case Success(res) => Right(res)
      case Failure(ex) =>
        val msg = Option(ex.getMessage).getOrElse(ex.toString)
        logger.error(s"Transaction failed: $msg", ex)
        Left(msg)

  override def close(): Unit =
    dataSource match
      case c: AutoCloseable => c.close()
      case _                => ()

end DB
