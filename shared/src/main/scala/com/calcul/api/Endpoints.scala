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

  val callbackEndpoint: PublicEndpoint[String, Unit, (Option[String], String), Any] =
    endpoint.get
      .in("api" / "auth" / "callback")
      .in(query[String]("code"))
      .out(header[Option[String]]("Set-Cookie"))
      .out(stringBody)
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
  val analyzeMealEndpoint: PublicEndpoint[AnalyzeMealRequest, Unit, MealAnalysisResponse, Any] =
    endpoint.post
      .in("api" / "meals" / "analyze")
      .in(jsonBody[AnalyzeMealRequest])
      .out(jsonBody[MealAnalysisResponse])
      .summary("Analyze meal description/photo with Gemini Flash")

  val createMealEndpoint: PublicEndpoint[CreateMealRequest, Unit, Meal, Any] =
    endpoint.post
      .in("api" / "meals")
      .in(jsonBody[CreateMealRequest])
      .out(jsonBody[Meal])
      .summary("Save meal entry")

  val listMealsEndpoint: PublicEndpoint[String, Unit, List[Meal], Any] =
    endpoint.get
      .in("api" / "meals")
      .in(query[String]("date"))
      .out(jsonBody[List[Meal]])
      .summary("List meals for a date")

  val deleteMealEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.delete
      .in("api" / "meals" / path[String]("id"))
      .out(stringBody)
      .summary("Delete meal by ID")

  // --- Calorie Goals & Daily Aggregation Endpoints ---
  val getDailyCaloriesEndpoint: PublicEndpoint[String, Unit, DailyCalorieSummary, Any] =
    endpoint.get
      .in("api" / "calories" / "daily")
      .in(query[String]("date"))
      .out(jsonBody[DailyCalorieSummary])
      .summary("Get daily calorie summary and budget balance")

  val setDailyTargetEndpoint: PublicEndpoint[SetTargetRequest, Unit, DailyTarget, Any] =
    endpoint.put
      .in("api" / "calories" / "target")
      .in(jsonBody[SetTargetRequest])
      .out(jsonBody[DailyTarget])
      .summary("Set daily calorie target")

  // --- Weight Tracking Endpoints ---
  val recordWeightEndpoint: PublicEndpoint[RecordWeightRequest, Unit, DailyWeight, Any] =
    endpoint.post
      .in("api" / "weights")
      .in(jsonBody[RecordWeightRequest])
      .out(jsonBody[DailyWeight])
      .summary("Record daily weight")

  val getWeightsEndpoint: PublicEndpoint[(String, String), Unit, List[DailyWeight], Any] =
    endpoint.get
      .in("api" / "weights")
      .in(query[String]("from"))
      .in(query[String]("to"))
      .out(jsonBody[List[DailyWeight]])
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
