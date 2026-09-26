package ru.trett.calcul.server

import munit.FunSuite
import java.time.{Instant, LocalDate}
import java.util.UUID
import ru.trett.calcul.ai.GeminiService
import ru.trett.calcul.db.{TestPostgresContainer, UserRepository}
import ru.trett.calcul.model.*

class MealEndpointsSuite extends FunSuite:

  test("MealService handles analysis, creation, listing, and deletion") {
    TestPostgresContainer.clearData()
    val conn = TestPostgresContainer.newConnection()
    try
      val userRepo = new UserRepository(conn)
      val testUser = User(
        id = UUID.randomUUID(),
        googleId = "test-user-google",
        email = "test@example.com",
        name = "Test User",
        pictureUrl = None,
        createdAt = Instant.now()
      )
      userRepo.upsert(testUser)

      val gemini      = new GeminiService()
      val mealService = new MealService(conn, gemini)

      // 1. Analyze
      val analysis = mealService.analyze("2 eggs and avocado toast")
      assert(analysis.totalCalories > 0, "Calories should be positive")
      assert(analysis.items.nonEmpty, "Items should not be empty")

      // 2. Create meal
      val today = LocalDate.parse("2026-09-20")
      val req = CreateMealRequest(
        mealDate = today,
        description = "2 eggs and avocado toast",
        totalCalories = analysis.totalCalories,
        aiExplanation = analysis.explanation,
        items = analysis.items.map(it => CreateMealItem(it.name, it.calories))
      )
      val created = mealService.createMeal(testUser.id, req)
      assertEquals(created.totalCalories, analysis.totalCalories)
      assertEquals(created.items.size, analysis.items.size)

      // 3. List meals
      val list = mealService.listMeals(testUser.id, today)
      assertEquals(list.size, 1)
      assertEquals(list.head.id, created.id)

      // 4. Delete meal
      val deleted = mealService.deleteMeal(testUser.id, created.id)
      assert(deleted, "Meal should be deleted")
      assertEquals(mealService.listMeals(testUser.id, today).size, 0)
    finally conn.close()
  }
