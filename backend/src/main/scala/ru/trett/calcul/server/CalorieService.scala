package ru.trett.calcul.server

import ru.trett.calcul.db.{DB, DailyTargetRepository, MealRepository}
import ru.trett.calcul.model.{DailyCalorieSummary, DailyTarget, SetTargetRequest}

import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class CalorieService(db: DB):

  def this(ds: DataSource) = this(DB(ds))

  private val targetRepo = new DailyTargetRepository(db)
  private val mealRepo   = new MealRepository(db)

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
