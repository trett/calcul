package com.calcul.server

import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import com.calcul.db.DailyWeightRepository
import com.calcul.model.{DailyWeight, RecordWeightRequest}

class WeightService(conn: Connection):
  private val weightRepo = new DailyWeightRepository(conn)

  def recordWeight(userId: UUID, req: RecordWeightRequest): DailyWeight =
    val entry = DailyWeight(userId, req.weighDate, req.weight, req.unit)
    weightRepo.recordWeight(entry)
    entry

  def getWeights(userId: UUID, fromDate: LocalDate, toDate: LocalDate): List[DailyWeight] =
    weightRepo.findWeightsInRange(userId, fromDate, toDate)
