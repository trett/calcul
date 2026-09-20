package com.calcul.server

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer
import com.calcul.ai.GeminiService
import com.calcul.api.Endpoints
import com.calcul.db.*
import java.sql.Connection
import java.time.LocalDate
import java.util.UUID

class ServerRoutes(conn: Connection, gemini: GeminiService = new GeminiService(None)):
  val userRepo: UserRepository          = new UserRepository(conn)
  val targetRepo: DailyTargetRepository = new DailyTargetRepository(conn)
  val mealRepo: MealRepository          = new MealRepository(conn)
  val weightRepo: DailyWeightRepository = new DailyWeightRepository(conn)
  val mealService: MealService          = new MealService(conn, gemini)
  val calorieService: CalorieService    = new CalorieService(conn)

  val defaultUserId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

  val loginRoute: ServerEndpoint[Any, Identity] =
    Endpoints.loginEndpoint.serverLogicSuccess[Identity](_ => "https://accounts.google.com/o/oauth2/v2/auth")

  val logoutRoute: ServerEndpoint[Any, Identity] =
    Endpoints.logoutEndpoint.serverLogicSuccess[Identity](_ => "Logged out successfully")

  val analyzeMealRoute: ServerEndpoint[Any, Identity] =
    Endpoints.analyzeMealEndpoint.serverLogicSuccess[Identity] { prompt =>
      mealService.analyze(prompt)
    }

  val createMealRoute: ServerEndpoint[Any, Identity] =
    Endpoints.createMealEndpoint.serverLogicSuccess[Identity] { req =>
      mealService.createMeal(defaultUserId, req)
    }

  val listMealsRoute: ServerEndpoint[Any, Identity] =
    Endpoints.listMealsEndpoint.serverLogicSuccess[Identity] { dateStr =>
      val date = LocalDate.parse(dateStr)
      mealService.listMeals(defaultUserId, date)
    }

  val deleteMealRoute: ServerEndpoint[Any, Identity] =
    Endpoints.deleteMealEndpoint.serverLogicSuccess[Identity] { idStr =>
      val mealId = UUID.fromString(idStr)
      if mealService.deleteMeal(defaultUserId, mealId) then s"Meal $idStr deleted"
      else s"Meal $idStr not found"
    }

  val getDailyCaloriesRoute: ServerEndpoint[Any, Identity] =
    Endpoints.getDailyCaloriesEndpoint.serverLogicSuccess[Identity] { dateStr =>
      val date = LocalDate.parse(dateStr)
      calorieService.getDailySummary(defaultUserId, date)
    }

  val setDailyTargetRoute: ServerEndpoint[Any, Identity] =
    Endpoints.setDailyTargetEndpoint.serverLogicSuccess[Identity] { req =>
      calorieService.setTarget(defaultUserId, req)
    }

  val allRoutes: List[ServerEndpoint[Any, Identity]] = List(
    loginRoute,
    logoutRoute,
    analyzeMealRoute,
    createMealRoute,
    listMealsRoute,
    deleteMealRoute,
    getDailyCaloriesRoute,
    setDailyTargetRoute
  )

  def createServer(host: String = "0.0.0.0", port: Int = 8080): NettySyncServer =
    NettySyncServer()
      .host(host)
      .port(port)
      .addEndpoints(allRoutes)
