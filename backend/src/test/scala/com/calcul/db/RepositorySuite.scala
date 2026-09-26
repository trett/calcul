package com.calcul.db

import munit.FunSuite
import java.time.{Instant, LocalDate}
import java.util.UUID
import com.calcul.model.*

class RepositorySuite extends FunSuite:

  private def withRepos(
      testCode: (UserRepository, DailyTargetRepository, MealRepository, DailyWeightRepository) => Unit
  ): Unit =
    TestPostgresContainer.clearData()
    val conn = TestPostgresContainer.newConnection()
    try
      val userRepo   = new UserRepository(conn)
      val targetRepo = new DailyTargetRepository(conn)
      val mealRepo   = new MealRepository(conn)
      val weightRepo = new DailyWeightRepository(conn)
      testCode(userRepo, targetRepo, mealRepo, weightRepo)
    finally conn.close()

  test("UserRepository upserts and finds users by ID and Google ID") {
    withRepos { (userRepo, _, _, _) =>
      val userId = UUID.randomUUID()
      val user = User(
        id = userId,
        googleId = "g-1001",
        email = "alice@example.com",
        name = "Alice",
        pictureUrl = Some("https://example.com/alice.jpg"),
        createdAt = Instant.now()
      )

      userRepo.upsert(user)
      val foundById = userRepo.findById(userId)
      assertEquals(foundById.map(_.email), Some("alice@example.com"))

      val foundByGoogle = userRepo.findByGoogleId("g-1001")
      assertEquals(foundByGoogle.map(_.name), Some("Alice"))

      val updated = user.copy(name = "Alice In Chains")
      userRepo.upsert(updated)
      assertEquals(userRepo.findById(userId).map(_.name), Some("Alice In Chains"))
    }
  }

  test("UserRepository updates, retrieves, and clears encrypted Gemini API key") {
    withRepos { (userRepo, _, _, _) =>
      val userId = UUID.randomUUID()
      val user = User(
        id = userId,
        googleId = "g-key-test",
        email = "keytest@example.com",
        name = "Key User",
        pictureUrl = None,
        createdAt = Instant.now()
      )
      userRepo.upsert(user)

      assertEquals(userRepo.getEncryptedGeminiKey(userId), None)

      userRepo.updateGeminiKey(userId, "enc-key-12345")
      assertEquals(userRepo.getEncryptedGeminiKey(userId), Some("enc-key-12345"))

      val userWithKey = userRepo.findById(userId)
      assertEquals(userWithKey.flatMap(_.encryptedGeminiApiKey), Some("enc-key-12345"))

      userRepo.clearGeminiKey(userId)
      assertEquals(userRepo.getEncryptedGeminiKey(userId), None)
      assertEquals(userRepo.findById(userId).flatMap(_.encryptedGeminiApiKey), None)
    }
  }

  test("DailyTargetRepository sets and retrieves daily targets") {
    withRepos { (userRepo, targetRepo, _, _) =>
      val userId = UUID.randomUUID()
      val user   = User(userId, "g-2002", "bob@example.com", "Bob", None, Instant.now())
      userRepo.upsert(user)

      val today = LocalDate.parse("2026-09-20")
      targetRepo.setTarget(userId, today, 2100)
      val found = targetRepo.findTarget(userId, today)
      assertEquals(found.map(_.calorieTarget), Some(2100))

      targetRepo.setTarget(userId, today, 1950)
      assertEquals(targetRepo.findTarget(userId, today).map(_.calorieTarget), Some(1950))
    }
  }

  test("MealRepository inserts, lists and deletes meals with items") {
    withRepos { (userRepo, _, mealRepo, _) =>
      val userId = UUID.randomUUID()
      val user   = User(userId, "g-3003", "carol@example.com", "Carol", None, Instant.now())
      userRepo.upsert(user)

      val mealId = UUID.randomUUID()
      val today  = LocalDate.parse("2026-09-20")
      val item1  = MealItem(UUID.randomUUID(), mealId, "Salmon 200g", 400)
      val item2  = MealItem(UUID.randomUUID(), mealId, "Quinoa 150g", 220)
      val meal = Meal(
        id = mealId,
        userId = userId,
        loggedAt = Instant.now(),
        mealDate = today,
        description = "Salmon with Quinoa",
        imagePath = None,
        totalCalories = 620,
        aiExplanation = "Nutritious dinner with omega-3",
        items = List(item1, item2)
      )

      mealRepo.insertMeal(meal)
      val meals = mealRepo.findMealsByDate(userId, today)
      assertEquals(meals.size, 1)
      assertEquals(meals.head.items.size, 2)
      assertEquals(meals.head.totalCalories, 620)

      val deleted = mealRepo.deleteMeal(userId, mealId)
      assert(deleted)
      assertEquals(mealRepo.findMealsByDate(userId, today).size, 0)
    }
  }

  test("DailyWeightRepository records and queries weights in date range") {
    withRepos { (userRepo, _, _, weightRepo) =>
      val userId = UUID.randomUUID()
      val user   = User(userId, "g-4004", "dan@example.com", "Dan", None, Instant.now())
      userRepo.upsert(user)

      val d1 = LocalDate.parse("2026-09-18")
      val d2 = LocalDate.parse("2026-09-19")
      val d3 = LocalDate.parse("2026-09-20")

      weightRepo.recordWeight(DailyWeight(userId, d1, BigDecimal("80.20"), "kg"))
      weightRepo.recordWeight(DailyWeight(userId, d2, BigDecimal("79.90"), "kg"))
      weightRepo.recordWeight(DailyWeight(userId, d3, BigDecimal("79.50"), "kg"))

      val range = weightRepo.findWeightsInRange(userId, d1, d2)
      assertEquals(range.size, 2)
      assertEquals(range.map(_.weight), List(BigDecimal("80.20"), BigDecimal("79.90")))
    }
  }
