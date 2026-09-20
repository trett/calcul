package com.calcul.server

import munit.FunSuite
import java.time.{Instant, LocalDate}
import java.util.UUID
import com.calcul.db.{MealRepository, TestPostgresContainer, UserRepository}
import com.calcul.model.*

class CalorieAggregationSuite extends FunSuite:

  test("CalorieService computes daily calorie totals, target comparison and remaining balance") {
    TestPostgresContainer.clearData()
    val conn = TestPostgresContainer.newConnection()
    try
      val userRepo       = new UserRepository(conn)
      val mealRepo       = new MealRepository(conn)
      val calorieService = new CalorieService(conn)

      val userId = UUID.randomUUID()
      userRepo.upsert(User(userId, "g-cal-1", "user@cal.com", "Calorie User", None, Instant.now()))

      val testDate = LocalDate.parse("2026-09-20")

      // 1. Initially without target or meals
      val initialSummary = calorieService.getDailySummary(userId, testDate)
      assertEquals(initialSummary.calorieTarget, 2000) // Default fallback target
      assertEquals(initialSummary.totalConsumed, 0)
      assertEquals(initialSummary.remainingCalories, 2000)
      assertEquals(initialSummary.mealsCount, 0)

      // 2. Set explicit target
      val target = calorieService.setTarget(userId, SetTargetRequest(testDate, 2250))
      assertEquals(target.calorieTarget, 2250)

      // 3. Add meals
      mealRepo.insertMeal(
        Meal(
          id = UUID.randomUUID(),
          userId = userId,
          loggedAt = Instant.now(),
          mealDate = testDate,
          description = "Breakfast",
          imagePath = None,
          totalCalories = 450,
          aiExplanation = "Eggs and coffee",
          items = List(MealItem(UUID.randomUUID(), UUID.randomUUID(), "Eggs", 450))
        )
      )
      mealRepo.insertMeal(
        Meal(
          id = UUID.randomUUID(),
          userId = userId,
          loggedAt = Instant.now(),
          mealDate = testDate,
          description = "Lunch",
          imagePath = None,
          totalCalories = 750,
          aiExplanation = "Chicken bowl",
          items = List(MealItem(UUID.randomUUID(), UUID.randomUUID(), "Bowl", 750))
        )
      )

      // 4. Check updated summary
      val updatedSummary = calorieService.getDailySummary(userId, testDate)
      assertEquals(updatedSummary.calorieTarget, 2250)
      assertEquals(updatedSummary.totalConsumed, 1200)
      assertEquals(updatedSummary.remainingCalories, 1050)
      assertEquals(updatedSummary.mealsCount, 2)
    finally conn.close()
  }
