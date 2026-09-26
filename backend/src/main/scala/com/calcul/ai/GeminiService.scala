package com.calcul.ai

import org.slf4j.LoggerFactory
import scala.concurrent.duration.*
import scala.util.Try
import sttp.client4.*
import sttp.model.{StatusCode, Uri}
import com.calcul.model.{AnalyzedItem, MealAnalysisResponse}

class GeminiService(
    backend: SyncBackend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
):

  private val logger = LoggerFactory.getLogger(getClass)

  private val ModelsEndpoint: Uri =
    uri"https://generativelanguage.googleapis.com/v1beta/models?pageSize=1"

  private val activeModel: java.util.concurrent.atomic.AtomicReference[Option[String]] =
    new java.util.concurrent.atomic.AtomicReference(sys.env.get("GEMINI_MODEL").filter(_.trim.nonEmpty))

  private def formatModel(model: String): String =
    val clean = model.trim.stripPrefix("/")
    if clean.startsWith("models/") then clean else s"models/$clean"

  private def discoverModel(apiKey: String): String =
    activeModel.get() match
      case Some(m) => formatModel(m)
      case None =>
        val resolved = fetchAvailableModel(apiKey).getOrElse("models/gemini-2.5-flash")
        activeModel.set(Some(resolved))
        resolved

  private def fetchAvailableModel(apiKey: String): Option[String] =
    Try {
      val response = basicRequest
        .get(uri"https://generativelanguage.googleapis.com/v1beta/models?pageSize=50")
        .header("x-goog-api-key", apiKey)
        .readTimeout(10.seconds)
        .response(asStringAlways)
        .send(backend)

      if response.code == StatusCode.Ok then
        val json   = ujson.read(response.body)
        val models = json.obj.get("models").map(_.arr.toList).getOrElse(Nil)
        val validModels = models
          .filter { m =>
            val methods = m.obj.get("supportedGenerationMethods").map(_.arr.map(_.str).toList).getOrElse(Nil)
            methods.contains("generateContent")
          }
          .map(_("name").str)

        val flashModel = validModels
          .find(m => m.contains("flash") && (m.contains("2.5") || m.contains("2.0") || m.contains("3.")))
          .orElse(validModels.find(_.contains("flash")))
          .orElse(validModels.headOption)

        flashModel.foreach(m => logger.info(s"Discovered available Gemini model for generateContent: $m"))
        flashModel
      else
        logger.warn(s"ListModels call returned HTTP ${response.code}: ${response.body}")
        None
    }.toOption.flatten

  def validateKey(key: String): Either[String, Unit] =
    val cleaned = key.trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'").trim
    if cleaned.isEmpty then Left("API key cannot be empty")
    else if cleaned.startsWith("mock-") || cleaned.startsWith("demo-") then
      logger.info("Accepting mock/demo Gemini API key for validation")
      Right(())
    else
      Try {
        val response = basicRequest
          .get(ModelsEndpoint)
          .header("x-goog-api-key", cleaned)
          .readTimeout(15.seconds)
          .response(asStringAlways)
          .send(backend)

        if response.code == StatusCode.Ok then
          logger.info("Gemini API key validated successfully via Google Generative Language API")
          Right(())
        else
          val status = response.code
          val body   = response.body
          logger.warn(s"Gemini API key validation call returned HTTP $status: $body")
          val detail =
            Try(ujson.read(body)("error")("message").str).getOrElse(s"HTTP $status")
          Left(detail)
      }.toEither.left.map { ex =>
        logger.error("Failed to reach Gemini API for key validation", ex)
        s"Failed to reach Gemini API: ${ex.getMessage}"
      }.flatten

  def analyzeMeal(
      description: Option[String],
      base64Image: Option[String] = None,
      mimeType: Option[String] = None,
      userApiKey: Option[String] = None
  ): MealAnalysisResponse =
    userApiKey.filter(_.trim.nonEmpty) match
      case Some(key) =>
        Try(
          callGeminiApi(key.trim, description.getOrElse("Meal photo nutritional analysis"), base64Image, mimeType)
        ) match
          case scala.util.Success(res) => res
          case scala.util.Failure(ex) =>
            logger.warn(s"Gemini API call failed with exception, falling back to local estimation: ${ex.getMessage}")
            fallbackEstimation(description.getOrElse("Meal"))
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

    val model       = discoverModel(key)
    val endpointUri = Uri.unsafeParse(s"https://generativelanguage.googleapis.com/v1beta/$model:generateContent")

    val response = basicRequest
      .post(endpointUri)
      .contentType("application/json")
      .header("x-goog-api-key", key.trim)
      .body(requestJson)
      .readTimeout(20.seconds)
      .response(asStringAlways)
      .send(backend)

    if response.code == StatusCode.Ok then
      val respJson    = ujson.read(response.body)
      val textContent = respJson("candidates")(0)("content")("parts")(0)("text").str
      GeminiService.parseGeminiResponse(textContent) match
        case Right(res) => res
        case Left(err) =>
          logger.warn(s"Failed to parse Gemini response JSON, falling back: $err")
          fallbackEstimation(prompt)
    else
      if response.code == StatusCode.NotFound then
        logger.warn(s"Gemini model $model returned 404 Not Found. Resetting discovered model cache.")
        if sys.env.get("GEMINI_MODEL").isEmpty then activeModel.set(None)
      logger.warn(s"Gemini API responded with HTTP status ${response.code}: ${response.body}")
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
