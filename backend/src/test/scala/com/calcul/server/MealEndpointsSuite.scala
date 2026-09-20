package com.calcul.server

import munit.FunSuite
import java.sql.{Connection, DriverManager}
import java.time.{Instant, LocalDate}
import java.util.UUID
import com.calcul.ai.GeminiService
import com.calcul.db.{TestDbInit, UserRepository}
import com.calcul.model.*

class MealEndpointsSuite extends FunSuite:

  test("MealService handles analysis, creation, listing, and deletion") {
    val jdbcUrl =
      s"jdbc:h2:mem:meal_endpoints_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    val conn: Connection = DriverManager.getConnection(jdbcUrl, "sa", "")
    try
      TestDbInit.initSchema(conn)
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

      val gemini      = new GeminiService(None)
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
