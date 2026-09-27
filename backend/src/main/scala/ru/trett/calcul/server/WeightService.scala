package ru.trett.calcul.server

import ru.trett.calcul.db.{DB, DailyWeightRepository}
import ru.trett.calcul.model.{DailyWeight, RecordWeightRequest}

import java.time.LocalDate
import java.util.UUID

class WeightService(db: DB):

  private val weightRepo = new DailyWeightRepository(db)

  def recordWeight(userId: UUID, req: RecordWeightRequest): DailyWeight =
    val entry = DailyWeight(userId, req.weighDate, req.weight, req.unit)
    weightRepo.recordWeight(entry)
    entry

  def getWeights(userId: UUID, fromDate: LocalDate, toDate: LocalDate): List[DailyWeight] =
    weightRepo.findWeightsInRange(userId, fromDate, toDate)
