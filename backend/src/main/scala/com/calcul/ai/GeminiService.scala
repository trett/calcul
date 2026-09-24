package com.calcul.ai

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import scala.util.Try
import com.calcul.model.{AnalyzedItem, MealAnalysisResponse}

class GeminiService(apiKey: Option[String] = sys.env.get("GEMINI_API_KEY")):

  def validateKey(key: String): Either[String, Unit] =
    val trimmed = key.trim
    if trimmed.isEmpty then Left("API key cannot be empty")
    else
      Try {
        val client = HttpClient.newHttpClient()
        val uri = URI.create(
          s"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$trimmed"
        )
        val requestJson = ujson
          .Obj(
            "contents" -> ujson.Arr(
              ujson.Obj("parts" -> ujson.Arr(ujson.Obj("text" -> ujson.Str("ping"))))
            )
          )
          .render()

        val request = HttpRequest
          .newBuilder()
          .uri(uri)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
          .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if response.statusCode() == 200 then Right(())
        else
          val status = response.statusCode()
          val body   = response.body()
          val detail =
            Try(ujson.read(body)("error")("message").str).getOrElse(s"HTTP $status")
          Left(s"Invalid Gemini API key: $detail")
      }.toEither.left.map(ex => s"Failed to reach Gemini API: ${ex.getMessage}").flatten

  def analyzeMeal(
      description: Option[String],
      base64Image: Option[String] = None,
      mimeType: Option[String] = None,
      userApiKey: Option[String] = None
  ): MealAnalysisResponse =
    val effectiveKey = userApiKey.filter(_.trim.nonEmpty).orElse(apiKey.filter(_.trim.nonEmpty))
    effectiveKey match
      case Some(key) =>
        Try(callGeminiApi(key.trim, description.getOrElse("Meal photo nutritional analysis"), base64Image, mimeType))
          .getOrElse(fallbackEstimation(description.getOrElse("Meal")))
      case None =>
        fallbackEstimation(description.getOrElse("Meal"))

  private def callGeminiApi(
      key: String,
      prompt: String,
      base64Image: Option[String],
      mimeType: Option[String]
  ): MealAnalysisResponse =
    val parts = collection.mutable.ListBuffer[ujson.Obj]()

    val systemInstruction =
      """You are an expert clinical dietitian and nutritional estimation engine.
        |Analyze the meal described and/or pictured. Identify each distinct food item and estimate its calories.
        |Return ONLY a valid, raw JSON object matching this exact schema:
        |{
        |  "items": [
        |    {"name": "Item name with portion", "calories": 150}
        |  ],
        |  "total_calories": 150,
        |  "explanation": "Brief reasoning of how portion sizes and calories were determined."
        |}
        |""".stripMargin

    parts += ujson.Obj("text" -> ujson.Str(s"$systemInstruction\nUser food description: $prompt"))

    base64Image.foreach { rawB64 =>
      val cleanB64 = if rawB64.contains(",") then rawB64.split(",", 2)(1) else rawB64
      val mt       = mimeType.getOrElse("image/jpeg")
      parts += ujson.Obj(
        "inline_data" -> ujson.Obj(
          "mime_type" -> ujson.Str(mt),
          "data"      -> ujson.Str(cleanB64)
        )
      )
    }

    val requestJson = ujson
      .Obj(
        "contents" -> ujson.Arr(
          ujson.Obj("parts" -> ujson.Arr.from(parts))
        ),
        "generationConfig" -> ujson.Obj(
          "response_mime_type" -> ujson.Str("application/json")
        )
      )
      .render()

    val client = HttpClient.newHttpClient()
    val uri = URI.create(
      s"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
    )
    val request = HttpRequest
      .newBuilder()
      .uri(uri)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
      .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    if response.statusCode() == 200 then
      val respJson    = ujson.read(response.body())
      val textContent = respJson("candidates")(0)("content")("parts")(0)("text").str
      GeminiService.parseGeminiResponse(textContent) match
        case Right(res) => res
        case Left(_)    => fallbackEstimation(prompt)
    else fallbackEstimation(prompt)

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
