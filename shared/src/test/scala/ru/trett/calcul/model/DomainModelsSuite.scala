package ru.trett.calcul.model

import munit.FunSuite
import upickle.default.*
import java.time.{Instant, LocalDate}
import java.util.UUID

class DomainModelsSuite extends FunSuite:

  test("User round-trip JSON serialization") {
    val user = User(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      googleId = "google-12345",
      email = "user@example.com",
      name = "Test User",
      pictureUrl = Some("https://example.com/avatar.jpg"),
      createdAt = Instant.parse("2026-09-20T10:00:00Z")
    )

    val json    = write(user)
    val decoded = read[User](json)
    assertEquals(decoded, user)
  }

  test("Meal and MealItem round-trip JSON serialization") {
    val item1 = MealItem(
      id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
      mealId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
      itemName = "Scrambled Eggs (2)",
      estimatedCalories = 180
    )
    val item2 = MealItem(
      id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
      mealId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
      itemName = "Sourdough Toast (1 slice)",
      estimatedCalories = 140
    )

    val meal = Meal(
      id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
      userId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      loggedAt = Instant.parse("2026-09-20T08:30:00Z"),
      mealDate = LocalDate.parse("2026-09-20"),
      description = "2 scrambled eggs and 1 slice toast",
      imagePath = None,
      totalCalories = 320,
      aiExplanation = "Standard breakfast items estimated accurately",
      items = List(item1, item2)
    )

    val json    = write(meal)
    val decoded = read[Meal](json)
    assertEquals(decoded, meal)
  }

  test("DailyTarget and DailyCalorieSummary round-trip JSON serialization") {
    val target = DailyTarget(
      userId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      targetDate = LocalDate.parse("2026-09-20"),
      calorieTarget = 2200
    )
    val jsonTarget    = write(target)
    val decodedTarget = read[DailyTarget](jsonTarget)
    assertEquals(decodedTarget, target)

    val summary = DailyCalorieSummary(
      targetDate = LocalDate.parse("2026-09-20"),
      calorieTarget = 2200,
      totalConsumed = 1450,
      remainingCalories = 750,
      mealsCount = 3
    )
    val jsonSummary    = write(summary)
    val decodedSummary = read[DailyCalorieSummary](jsonSummary)
    assertEquals(decodedSummary, summary)
  }

  test("DailyWeight round-trip JSON serialization") {
    val weight = DailyWeight(
      userId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      weighDate = LocalDate.parse("2026-09-20"),
      weight = BigDecimal("78.50"),
      unit = "kg"
    )
    val json    = write(weight)
    val decoded = read[DailyWeight](json)
    assertEquals(decoded, weight)
  }

  test("MealAnalysisResponse round-trip JSON serialization") {
    val analysis = MealAnalysisResponse(
      items = List(
        AnalyzedItem("Grilled Chicken Breast 150g", 250),
        AnalyzedItem("Steamed Broccoli 100g", 35)
      ),
      totalCalories = 285,
      explanation = "Healthy lean protein and vegetable portion"
    )
    val json    = write(analysis)
    val decoded = read[MealAnalysisResponse](json)
    assertEquals(decoded, analysis)
  }

  test("UserSummary with Gemini key info round-trip JSON serialization") {
    val summary = UserSummary(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      email = "user@example.com",
      name = "Test User",
      pictureUrl = Some("https://example.com/avatar.jpg"),
      hasGeminiKey = true,
      maskedGeminiKey = Some("••••••••••••1234")
    )
    val json    = write(summary)
    val decoded = read[UserSummary](json)
    assertEquals(decoded, summary)
  }

  test("GeminiKeyStatus and SaveGeminiKeyRequest round-trip JSON serialization") {
    val status     = GeminiKeyStatus(hasKey = true, maskedKey = Some("••••••••••••5678"))
    val jsonStatus = write(status)
    assertEquals(read[GeminiKeyStatus](jsonStatus), status)

    val req     = SaveGeminiKeyRequest(apiKey = "AIzaSyD-sample-key")
    val jsonReq = write(req)
    assertEquals(read[SaveGeminiKeyRequest](jsonReq), req)
  }
