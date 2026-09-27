package com.augustnagro.magnum

import java.sql.Connection

object MagnumBridge:
  def dbCon(con: Connection, sqlLogger: SqlLogger = SqlLogger.Default): DbCon =
    new DbCon(con, sqlLogger)

  def dbTx(con: Connection, sqlLogger: SqlLogger = SqlLogger.Default): DbTx =
    new DbTx(con, sqlLogger)
