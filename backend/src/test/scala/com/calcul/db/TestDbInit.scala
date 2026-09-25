package com.calcul.db

import java.sql.Connection

object TestDbInit:

  def initSchema(conn: Connection): Set[String] =
    DatabaseInit.initSchema(conn)
