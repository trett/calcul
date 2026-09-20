package com.calcul.server

import java.sql.Connection
import java.time.{Instant, LocalDate}
import java.util.UUID
import com.calcul.ai.GeminiService
import com.calcul.db.MealRepository
import com.calcul.model.*

class MealService(conn: Connection, gemini: GeminiService):
  private val mealRepo = new MealRepository(conn)

  def analyze(description: String, imageBase64: Option[String] = None): MealAnalysisResponse =
    gemini.analyzeMeal(Some(description), imageBase64)

  def createMeal(userId: UUID, req: CreateMealRequest): Meal =
    val mealId = UUID.randomUUID()
    val items = req.items.map { it =>
      MealItem(
        id = UUID.randomUUID(),
        mealId = mealId,
        itemName = it.itemName,
        estimatedCalories = it.estimatedCalories
      )
    }

    val meal = Meal(
      id = mealId,
      userId = userId,
      loggedAt = Instant.now(),
      mealDate = req.mealDate,
      description = req.description,
      imagePath = None,
      totalCalories = req.totalCalories,
      aiExplanation = req.aiExplanation,
      items = items
    )

    mealRepo.insertMeal(meal)
    meal

  def listMeals(userId: UUID, date: LocalDate): List[Meal] =
    mealRepo.findMealsByDate(userId, date)

  def deleteMeal(userId: UUID, mealId: UUID): Boolean =
    mealRepo.deleteMeal(userId, mealId)
