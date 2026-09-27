package ru.trett.calcul.server

import org.slf4j.LoggerFactory
import ru.trett.calcul.ai.GeminiService
import ru.trett.calcul.db.{DB, MealRepository}
import ru.trett.calcul.model.*

import java.time.{Instant, LocalDate}
import java.util.UUID

class MealService(db: DB, gemini: GeminiService):

  private val logger = LoggerFactory.getLogger(getClass)

  private val mealRepo = new MealRepository(db)

  def analyze(req: AnalyzeMealRequest, userApiKey: Option[String]): MealAnalysisResponse =
    gemini.analyzeMeal(req.description, req.imageBase64, req.mimeType, userApiKey = userApiKey)

  def analyze(req: AnalyzeMealRequest): MealAnalysisResponse =
    analyze(req, None)

  def analyze(description: String): MealAnalysisResponse =
    analyze(description, None, None)

  def analyze(
      description: String,
      imageBase64: Option[String],
      userApiKey: Option[String]
  ): MealAnalysisResponse =
    gemini.analyzeMeal(Some(description), imageBase64, userApiKey = userApiKey)

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

    mealRepo.insertMeal(meal) match
      case Left(err) => logger.error(s"Failed to insert meal into database: $err")
      case Right(_)  => ()
    meal

  def listMeals(userId: UUID, date: LocalDate): List[Meal] =
    mealRepo.findMealsByDate(userId, date)

  def deleteMeal(userId: UUID, mealId: UUID): Boolean =
    mealRepo.deleteMeal(userId, mealId)
