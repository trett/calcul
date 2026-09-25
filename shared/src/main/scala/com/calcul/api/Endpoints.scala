package com.calcul.api

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.upickle.*
import com.calcul.model.*

object Endpoints:

  // --- Auth Endpoints ---
  val loginEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("api" / "auth" / "login")
      .out(stringBody)
      .summary("Initiate Google OAuth2 login")

  val callbackEndpoint: PublicEndpoint[String, Unit, (StatusCode, Option[String], Option[String], String), Any] =
    endpoint.get
      .in("api" / "auth" / "callback")
      .in(query[String]("code"))
      .out(statusCode)
      .out(header[Option[String]]("Location"))
      .out(header[Option[String]]("Set-Cookie"))
      .out(htmlBodyUtf8)
      .summary("Google OAuth2 callback exchange")

  val meEndpoint: PublicEndpoint[Option[String], (StatusCode, String), UserSummary, Any] =
    endpoint.get
      .in("api" / "auth" / "me")
      .in(cookie[Option[String]]("session"))
      .out(jsonBody[UserSummary])
      .errorOut(statusCode.and(stringBody))
      .summary("Get current authenticated user")

  val logoutEndpoint: PublicEndpoint[Unit, Unit, (Option[String], String), Any] =
    endpoint.post
      .in("api" / "auth" / "logout")
      .out(header[Option[String]]("Set-Cookie"))
      .out(stringBody)
      .summary("Logout user session")

  // --- Meals & AI Analysis Endpoints ---
  val analyzeMealEndpoint
      : PublicEndpoint[(Option[String], AnalyzeMealRequest), (StatusCode, String), MealAnalysisResponse, Any] =
    endpoint.post
      .in("api" / "meals" / "analyze")
      .in(cookie[Option[String]]("session"))
      .in(jsonBody[AnalyzeMealRequest])
      .out(jsonBody[MealAnalysisResponse])
      .errorOut(statusCode.and(stringBody))
      .summary("Analyze meal description/photo with Gemini Flash")

  val createMealEndpoint: PublicEndpoint[(Option[String], CreateMealRequest), (StatusCode, String), Meal, Any] =
    endpoint.post
      .in("api" / "meals")
      .in(cookie[Option[String]]("session"))
      .in(jsonBody[CreateMealRequest])
      .out(jsonBody[Meal])
      .errorOut(statusCode.and(stringBody))
      .summary("Save meal entry")

  val listMealsEndpoint: PublicEndpoint[(Option[String], String), (StatusCode, String), List[Meal], Any] =
    endpoint.get
      .in("api" / "meals")
      .in(cookie[Option[String]]("session"))
      .in(query[String]("date"))
      .out(jsonBody[List[Meal]])
      .errorOut(statusCode.and(stringBody))
      .summary("List meals for a date")

  val deleteMealEndpoint: PublicEndpoint[(Option[String], String), (StatusCode, String), String, Any] =
    endpoint.delete
      .in(cookie[Option[String]]("session"))
      .in("api" / "meals" / path[String]("id"))
      .out(stringBody)
      .errorOut(statusCode.and(stringBody))
      .summary("Delete meal by ID")

  // --- Calorie Goals & Daily Aggregation Endpoints ---
  val getDailyCaloriesEndpoint
      : PublicEndpoint[(Option[String], String), (StatusCode, String), DailyCalorieSummary, Any] =
    endpoint.get
      .in("api" / "calories" / "daily")
      .in(cookie[Option[String]]("session"))
      .in(query[String]("date"))
      .out(jsonBody[DailyCalorieSummary])
      .errorOut(statusCode.and(stringBody))
      .summary("Get daily calorie summary and budget balance")

  val setDailyTargetEndpoint
      : PublicEndpoint[(Option[String], SetTargetRequest), (StatusCode, String), DailyTarget, Any] =
    endpoint.put
      .in("api" / "calories" / "target")
      .in(cookie[Option[String]]("session"))
      .in(jsonBody[SetTargetRequest])
      .out(jsonBody[DailyTarget])
      .errorOut(statusCode.and(stringBody))
      .summary("Set daily calorie target")

  // --- Weight Tracking Endpoints ---
  val recordWeightEndpoint
      : PublicEndpoint[(Option[String], RecordWeightRequest), (StatusCode, String), DailyWeight, Any] =
    endpoint.post
      .in("api" / "weights")
      .in(cookie[Option[String]]("session"))
      .in(jsonBody[RecordWeightRequest])
      .out(jsonBody[DailyWeight])
      .errorOut(statusCode.and(stringBody))
      .summary("Record daily weight")

  val getWeightsEndpoint
      : PublicEndpoint[(Option[String], String, String), (StatusCode, String), List[DailyWeight], Any] =
    endpoint.get
      .in("api" / "weights")
      .in(cookie[Option[String]]("session"))
      .in(query[String]("from"))
      .in(query[String]("to"))
      .out(jsonBody[List[DailyWeight]])
      .errorOut(statusCode.and(stringBody))
      .summary("Get weight entries in date range")

  // --- User Settings Endpoints ---
  val getGeminiKeyStatusEndpoint: PublicEndpoint[Option[String], (StatusCode, String), GeminiKeyStatus, Any] =
    endpoint.get
      .in("api" / "user" / "settings" / "gemini-key")
      .in(cookie[Option[String]]("session"))
      .out(jsonBody[GeminiKeyStatus])
      .errorOut(statusCode.and(stringBody))
      .summary("Get current Gemini API key status for authenticated user")

  val saveGeminiKeyEndpoint
      : PublicEndpoint[(Option[String], SaveGeminiKeyRequest), (StatusCode, String), GeminiKeyStatus, Any] =
    endpoint.post
      .in("api" / "user" / "settings" / "gemini-key")
      .in(cookie[Option[String]]("session"))
      .in(jsonBody[SaveGeminiKeyRequest])
      .out(jsonBody[GeminiKeyStatus])
      .errorOut(statusCode.and(stringBody))
      .summary("Validate and save Gemini API key for authenticated user")

  val deleteGeminiKeyEndpoint: PublicEndpoint[Option[String], (StatusCode, String), String, Any] =
    endpoint.delete
      .in("api" / "user" / "settings" / "gemini-key")
      .in(cookie[Option[String]]("session"))
      .out(stringBody)
      .errorOut(statusCode.and(stringBody))
      .summary("Delete saved Gemini API key for authenticated user")

  val allEndpoints: List[AnyEndpoint] = List(
    loginEndpoint,
    callbackEndpoint,
    meEndpoint,
    logoutEndpoint,
    analyzeMealEndpoint,
    createMealEndpoint,
    listMealsEndpoint,
    deleteMealEndpoint,
    getDailyCaloriesEndpoint,
    setDailyTargetEndpoint,
    recordWeightEndpoint,
    getWeightsEndpoint,
    getGeminiKeyStatusEndpoint,
    saveGeminiKeyEndpoint,
    deleteGeminiKeyEndpoint
  )
