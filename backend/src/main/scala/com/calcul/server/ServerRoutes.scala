package com.calcul.server

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer
import com.calcul.ai.GeminiService
import com.calcul.api.Endpoints
import com.calcul.auth.{AuthConfig, AuthService}
import com.calcul.db.*
import com.calcul.model.UserSummary
import java.sql.Connection
import java.time.LocalDate
import java.util.UUID

class ServerRoutes(
    conn: Connection,
    gemini: GeminiService = new GeminiService(None),
    authConfig: AuthConfig = AuthConfig.fromEnv()
):
  val userRepo: UserRepository          = new UserRepository(conn)
  val targetRepo: DailyTargetRepository = new DailyTargetRepository(conn)
  val mealRepo: MealRepository          = new MealRepository(conn)
  val weightRepo: DailyWeightRepository = new DailyWeightRepository(conn)
  val mealService: MealService          = new MealService(conn, gemini)
  val calorieService: CalorieService    = new CalorieService(conn)
  val weightService: WeightService      = new WeightService(conn)
  val authService: AuthService          = new AuthService(userRepo, authConfig)

  val defaultUserId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

  val loginRoute: ServerEndpoint[Any, Identity] =
    Endpoints.loginEndpoint.serverLogicSuccess[Identity](_ => authService.loginUrl("auth_state"))

  val callbackRoute: ServerEndpoint[Any, Identity] =
    Endpoints.callbackEndpoint.serverLogicSuccess[Identity] { _ =>
      authService.handleGoogleUser("google-demo-user", "user@example.com", "Demo User", None)
    }

  val meRoute: ServerEndpoint[Any, Identity] =
    Endpoints.meEndpoint.serverLogicSuccess[Identity] { _ =>
      userRepo.findById(defaultUserId) match
        case Some(u) => UserSummary(u.id, u.email, u.name, u.pictureUrl)
        case None    => UserSummary(defaultUserId, "demo@example.com", "Demo User", None)
    }

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

  val recordWeightRoute: ServerEndpoint[Any, Identity] =
    Endpoints.recordWeightEndpoint.serverLogicSuccess[Identity] { req =>
      weightService.recordWeight(defaultUserId, req)
    }

  val getWeightsRoute: ServerEndpoint[Any, Identity] =
    Endpoints.getWeightsEndpoint.serverLogicSuccess[Identity] { case (fromStr, toStr) =>
      val fromDate = LocalDate.parse(fromStr)
      val toDate   = LocalDate.parse(toStr)
      weightService.getWeights(defaultUserId, fromDate, toDate)
    }

  val allRoutes: List[ServerEndpoint[Any, Identity]] = List(
    loginRoute,
    callbackRoute,
    meRoute,
    logoutRoute,
    analyzeMealRoute,
    createMealRoute,
    listMealsRoute,
    deleteMealRoute,
    getDailyCaloriesRoute,
    setDailyTargetRoute,
    recordWeightRoute,
    getWeightsRoute
  )

  def createServer(host: String = "0.0.0.0", port: Int = 8080): NettySyncServer =
    NettySyncServer()
      .host(host)
      .port(port)
      .addEndpoints(allRoutes)
