package ru.trett.calcul

import munit.FunSuite
import java.util.UUID
import ru.trett.calcul.model.UserSummary

class MealAnalysisGatingSuite extends FunSuite:

  test("canAnalyze checks that authenticated user has a configured Gemini API key") {
    MockDom.install()

    val userWithoutKey = UserSummary(
      id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
      email = "nokey@example.com",
      name = "No Key User",
      pictureUrl = None,
      hasGeminiKey = false,
      maskedGeminiKey = None
    )

    val userWithKey = userWithoutKey.copy(
      hasGeminiKey = true,
      maskedGeminiKey = Some("••••••••••••5678")
    )

    assertEquals(MealIngestionView.canAnalyze(None), false)
    assertEquals(MealIngestionView.canAnalyze(Some(userWithoutKey)), false)
    assertEquals(MealIngestionView.canAnalyze(Some(userWithKey)), true)
  }
