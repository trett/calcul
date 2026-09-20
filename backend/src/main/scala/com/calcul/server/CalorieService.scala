package com.calcul.server

import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import com.calcul.db.{DailyTargetRepository, MealRepository}
import com.calcul.model.{DailyCalorieSummary, DailyTarget, SetTargetRequest}

class CalorieService(conn: Connection):
  private val targetRepo = new DailyTargetRepository(conn)
  private val mealRepo   = new MealRepository(conn)

  def setTarget(userId: UUID, req: SetTargetRequest): DailyTarget =
    targetRepo.setTarget(userId, req.targetDate, req.calorieTarget)
    DailyTarget(userId, req.targetDate, req.calorieTarget)

  def getDailySummary(userId: UUID, date: LocalDate): DailyCalorieSummary =
    val target   = targetRepo.findTarget(userId, date).map(_.calorieTarget).getOrElse(2000)
    val meals    = mealRepo.findMealsByDate(userId, date)
    val consumed = meals.map(_.totalCalories).sum
    DailyCalorieSummary(
      targetDate = date,
      calorieTarget = target,
      totalConsumed = consumed,
      remainingCalories = target - consumed,
      mealsCount = meals.size
    )
