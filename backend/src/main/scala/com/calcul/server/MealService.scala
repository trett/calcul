package com.calcul.server

import java.sql.Connection
import java.time.{Instant, LocalDate}
import java.util.UUID
import javax.sql.DataSource
import com.calcul.ai.GeminiService
import com.calcul.db.{DbTransactor, MealRepository}
import com.calcul.model.*

class MealService(transactor: DbTransactor, gemini: GeminiService):

  def this(ds: DataSource, gemini: GeminiService) = this(DbTransactor.fromDataSource(ds), gemini)
  def this(conn: Connection, gemini: GeminiService) = this(DbTransactor.fromConnection(conn), gemini)

  private val mealRepo = new MealRepository(transactor)

  def analyze(req: AnalyzeMealRequest): MealAnalysisResponse =
    gemini.analyzeMeal(req.description, req.imageBase64, req.mimeType)

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
