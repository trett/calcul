package com.calcul.ai

import munit.FunSuite
import com.calcul.model.MealAnalysisResponse

class GeminiServiceSuite extends FunSuite:

  test("parseGeminiResponse correctly extracts structured response from raw JSON") {
    val rawJson =
      """{
        |  "items": [
        |    {"name": "Poached Eggs (2)", "calories": 145},
        |    {"name": "Whole Wheat Toast (1)", "calories": 80},
        |    {"name": "Avocado (half)", "calories": 160}
        |  ],
        |  "total_calories": 385,
        |  "explanation": "Balanced breakfast with protein and healthy fats."
        |}
      """.stripMargin

    val result = GeminiService.parseGeminiResponse(rawJson)
    assert(result.isRight, s"Expected successful parsing, got: $result")
    val parsed: MealAnalysisResponse = result.toOption.get
    assertEquals(parsed.totalCalories, 385)
    assertEquals(parsed.items.size, 3)
    assertEquals(parsed.items.head.name, "Poached Eggs (2)")
    assertEquals(parsed.items.head.calories, 145)
  }

  test("parseGeminiResponse correctly handles markdown wrapped json blocks") {
    val markdownJson =
      """Here is the nutritional estimation:
        |```json
        |{
        |  "items": [
        |    {"name": "Greek Yogurt 200g", "calories": 130},
        |    {"name": "Blueberries 50g", "calories": 30}
        |  ],
        |  "total_calories": 160,
        |  "explanation": "High protein snack."
        |}
        |```
      """.stripMargin

    val result = GeminiService.parseGeminiResponse(markdownJson)
    assert(result.isRight)
    val parsed = result.toOption.get
    assertEquals(parsed.totalCalories, 160)
    assertEquals(parsed.items.size, 2)
  }

  test("parseGeminiResponse returns Left for unparseable input") {
    val invalid = "Sorry, I could not analyze this meal."
    val result  = GeminiService.parseGeminiResponse(invalid)
    assert(result.isLeft)
  }
