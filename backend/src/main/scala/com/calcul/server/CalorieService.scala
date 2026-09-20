package com.calcul.server

import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import com.calcul.db.{DailyTargetRepository, DbTransactor, MealRepository}
import com.calcul.model.{DailyCalorieSummary, DailyTarget, SetTargetRequest}

class CalorieService(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  private val targetRepo = new DailyTargetRepository(transactor)
  private val mealRepo   = new MealRepository(transactor)

  def setTarget(userId: UUID, req: SetTargetRequest): DailyTarget =
    targetRepo.setTarget(userId, req.targetDate, req.calorieTarget)
    DailyTarget(userId, req.targetDate, req.calorieTarget)

  def getDailySummary(userId: UUID, date: LocalDate): DailyCalorieSummary =
    val target            = targetRepo.findTarget(userId, date).map(_.calorieTarget).getOrElse(2000)
    val (consumed, count) = mealRepo.getDailyCalorieStats(userId, date)
    DailyCalorieSummary(
      targetDate = date,
      calorieTarget = target,
      totalConsumed = consumed,
      remainingCalories = target - consumed,
      mealsCount = count
    )
