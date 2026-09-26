package ru.trett.calcul.server

import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import ru.trett.calcul.db.{DailyWeightRepository, DbTransactor}
import ru.trett.calcul.model.{DailyWeight, RecordWeightRequest}

class WeightService(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  private val weightRepo = new DailyWeightRepository(transactor)

  def recordWeight(userId: UUID, req: RecordWeightRequest): DailyWeight =
    val entry = DailyWeight(userId, req.weighDate, req.weight, req.unit)
    weightRepo.recordWeight(entry)
    entry

  def getWeights(userId: UUID, fromDate: LocalDate, toDate: LocalDate): List[DailyWeight] =
    weightRepo.findWeightsInRange(userId, fromDate, toDate)
