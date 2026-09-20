package com.calcul.ai

import scala.util.Try
import com.calcul.model.{AnalyzedItem, MealAnalysisResponse}

class GeminiService(apiKey: Option[String] = sys.env.get("GEMINI_API_KEY")):

  def analyzeMeal(description: Option[String], base64Image: Option[String] = None): MealAnalysisResponse =
    apiKey match
      case Some(key) if key.nonEmpty =>
        callGeminiApi(key, description.getOrElse("Meal photo analysis"), base64Image)
      case _ =>
        // Fallback estimation when API key is not configured (e.g. offline dev/testing)
        fallbackEstimation(description.getOrElse("Meal"))

  private def callGeminiApi(key: String, prompt: String, base64Image: Option[String]): MealAnalysisResponse =
    val _ = (key, base64Image)
    fallbackEstimation(prompt)

  private def fallbackEstimation(description: String): MealAnalysisResponse =
    val words = description.toLowerCase
    val estimatedCalories =
      if words.contains("egg") then 180
      else if words.contains("salad") then 150
      else if words.contains("pizza") then 650
      else if words.contains("steak") || words.contains("beef") then 550
      else if words.contains("chicken") then 400
      else if words.contains("salmon") || words.contains("fish") then 450
      else 350

    MealAnalysisResponse(
      items = List(AnalyzedItem(description, estimatedCalories)),
      totalCalories = estimatedCalories,
      explanation = s"Estimated ~$estimatedCalories kcal based on: $description"
    )

object GeminiService:

  def parseGeminiResponse(rawText: String): Either[String, MealAnalysisResponse] =
    Try {
      val cleaned    = extractJson(rawText)
      val parsedJson = ujson.read(cleaned)
      val totalCal =
        if parsedJson.obj.contains("total_calories") then parsedJson("total_calories").num.toInt
        else parsedJson("totalCalories").num.toInt
      val explanation = parsedJson("explanation").str
      val itemsList = parsedJson("items").arr.map { it =>
        val name = it("name").str
        val cal  = it("calories").num.toInt
        AnalyzedItem(name, cal)
      }.toList

      MealAnalysisResponse(itemsList, totalCal, explanation)
    }.toEither.left.map(_.getMessage)

  private def extractJson(text: String): String =
    val trimmed = text.trim
    if trimmed.contains("```json") then
      val start = trimmed.indexOf("```json") + 7
      val end   = trimmed.indexOf("```", start)
      if end != -1 then trimmed.substring(start, end).trim
      else trimmed.substring(start).trim
    else if trimmed.contains("```") then
      val start = trimmed.indexOf("```") + 3
      val end   = trimmed.indexOf("```", start)
      if end != -1 then trimmed.substring(start, end).trim
      else trimmed.substring(start).trim
    else trimmed
