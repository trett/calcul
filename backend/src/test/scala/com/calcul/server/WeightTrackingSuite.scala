package com.calcul.server

import munit.FunSuite
import java.sql.{Connection, DriverManager}
import java.time.{Instant, LocalDate}
import java.util.UUID
import com.calcul.db.{TestDbInit, UserRepository}
import com.calcul.model.*

class WeightTrackingSuite extends FunSuite:

  test("WeightService records daily weights and retrieves range history") {
    val jdbcUrl =
      s"jdbc:h2:mem:weight_tracking_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    val conn: Connection = DriverManager.getConnection(jdbcUrl, "sa", "")
    try
      TestDbInit.initSchema(conn)
      val userRepo = new UserRepository(conn)
      val userId   = UUID.randomUUID()
      userRepo.upsert(User(userId, "g-weight-1", "user@weight.com", "Weight User", None, Instant.now()))

      val weightService = new WeightService(conn)

      val d1 = LocalDate.parse("2026-09-18")
      val d2 = LocalDate.parse("2026-09-19")
      val d3 = LocalDate.parse("2026-09-20")

      val w1 = weightService.recordWeight(userId, RecordWeightRequest(d1, BigDecimal("75.50"), "kg"))
      assertEquals(w1.weight, BigDecimal("75.50"), "First weight matches")

      weightService.recordWeight(userId, RecordWeightRequest(d2, BigDecimal("75.20"), "kg"))
      weightService.recordWeight(userId, RecordWeightRequest(d3, BigDecimal("74.90"), "kg"))

      val history = weightService.getWeights(userId, d1, d3)
      assertEquals(history.size, 3)
      val expected = List(BigDecimal("75.50"), BigDecimal("75.20"), BigDecimal("74.90"))
      assertEquals(history.map(_.weight), expected, "Weights in range match")
    finally conn.close()
  }
