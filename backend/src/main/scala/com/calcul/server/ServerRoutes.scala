package com.calcul.server

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer
import com.calcul.api.Endpoints
import com.calcul.db.*
import java.sql.Connection

class ServerRoutes(conn: Connection):
  val userRepo: UserRepository          = new UserRepository(conn)
  val targetRepo: DailyTargetRepository = new DailyTargetRepository(conn)
  val mealRepo: MealRepository          = new MealRepository(conn)
  val weightRepo: DailyWeightRepository = new DailyWeightRepository(conn)

  val loginRoute: ServerEndpoint[Any, Identity] =
    Endpoints.loginEndpoint.serverLogicSuccess[Identity](_ => "https://accounts.google.com/o/oauth2/v2/auth")

  val logoutRoute: ServerEndpoint[Any, Identity] =
    Endpoints.logoutEndpoint.serverLogicSuccess[Identity](_ => "Logged out successfully")

  val deleteMealRoute: ServerEndpoint[Any, Identity] =
    Endpoints.deleteMealEndpoint.serverLogicSuccess[Identity](id => s"Meal $id deleted")

  val allRoutes: List[ServerEndpoint[Any, Identity]] = List(
    loginRoute,
    logoutRoute,
    deleteMealRoute
  )

  def createServer(host: String = "0.0.0.0", port: Int = 8080): NettySyncServer =
    NettySyncServer()
      .host(host)
      .port(port)
      .addEndpoints(allRoutes)
