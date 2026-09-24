package com.calcul.server

import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import scala.util.{Try, Using}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.stringToPath
import com.calcul.ai.GeminiService
import com.calcul.api.Endpoints
import com.calcul.auth.{AuthConfig, AuthService, CryptoUtils}
import com.calcul.db.*
import com.calcul.model.{GeminiKeyStatus, User, UserSummary}

class ServerRoutes(
    transactor: DbTransactor,
    gemini: GeminiService = new GeminiService(None),
    authConfig: AuthConfig = AuthConfig.fromEnv()
):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds), new GeminiService(None), AuthConfig.fromEnv())
  def this(ds: DataSource, gemini: GeminiService, authConfig: AuthConfig) =
    this(DbTransactor.fromDataSource(ds), gemini, authConfig)
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn), new GeminiService(None), AuthConfig.fromEnv())
  def this(conn: Connection, gemini: GeminiService, authConfig: AuthConfig) =
    this(DbTransactor.fromConnection(conn), gemini, authConfig)

  val userRepo: UserRepository          = new UserRepository(transactor)
  val targetRepo: DailyTargetRepository = new DailyTargetRepository(transactor)
  val mealRepo: MealRepository          = new MealRepository(transactor)
  val weightRepo: DailyWeightRepository = new DailyWeightRepository(transactor)
  val mealService: MealService          = new MealService(transactor, gemini)
  val calorieService: CalorieService    = new CalorieService(transactor)
  val weightService: WeightService      = new WeightService(transactor)
  val authService: AuthService          = new AuthService(userRepo, authConfig)

  val defaultUserId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

  private def authenticate(sessionCookieOpt: Option[String]): Either[(StatusCode, String), User] =
    sessionCookieOpt.flatMap(authService.verifySessionToken).flatMap(userRepo.findById) match
      case Some(user) => Right(user)
      case None       => Left((StatusCode.Unauthorized, "Unauthorized"))

  val loginRoute: ServerEndpoint[Any, Identity] =
    Endpoints.loginEndpoint.serverLogicSuccess[Identity](_ => authService.loginUrl("auth_state"))

  val callbackRoute: ServerEndpoint[Any, Identity] =
    Endpoints.callbackEndpoint.serverLogicSuccess[Identity] { code =>
      val googleUserOpt = authService.exchangeGoogleCode(code)
      val userSummary = googleUserOpt match
        case Some(gu) =>
          authService.handleGoogleUser(gu.googleId, gu.email, gu.name, gu.pictureUrl)
        case None =>
          authService.handleGoogleUser("google-demo-user", "user@example.com", "Demo User", None)

      val sessionToken = authService.createSessionToken(userSummary.id)
      val cookieHeader = s"session=$sessionToken; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000"
      val redirectHtml =
        """<!DOCTYPE html>
          |<html>
          |<head>
          |  <meta http-equiv="refresh" content="0;url=/">
          |  <script>window.location.href="/";</script>
          |</head>
          |<body>
          |  <p>Logging in, please wait... <a href="/">Click here if not redirected</a>.</p>
          |</body>
          |</html>""".stripMargin
      (Some(cookieHeader), redirectHtml)
    }

  val meRoute =
    Endpoints.meEndpoint.serverLogic[Identity] { sessionCookieOpt =>
      authenticate(sessionCookieOpt).map { u =>
        val hasKey = u.encryptedGeminiApiKey.isDefined
        val masked = u.encryptedGeminiApiKey.flatMap { enc =>
          CryptoUtils.decrypt(enc, authConfig.sessionSecret).toOption.map(CryptoUtils.maskKey)
        }
        UserSummary(u.id, u.email, u.name, u.pictureUrl, hasKey, masked)
      }
    }

  val logoutRoute: ServerEndpoint[Any, Identity] =
    Endpoints.logoutEndpoint.serverLogicSuccess[Identity] { _ =>
      val clearCookie = "session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"
      (Some(clearCookie), "Logged out successfully")
    }

  val getGeminiKeyStatusRoute =
    Endpoints.getGeminiKeyStatusEndpoint.serverLogic[Identity] { sessionCookieOpt =>
      authenticate(sessionCookieOpt).map { user =>
        val encKeyOpt = userRepo.getEncryptedGeminiKey(user.id)
        val maskedKeyOpt = encKeyOpt.flatMap { enc =>
          CryptoUtils.decrypt(enc, authConfig.sessionSecret).toOption.map(CryptoUtils.maskKey)
        }
        GeminiKeyStatus(hasKey = encKeyOpt.isDefined, maskedKey = maskedKeyOpt)
      }
    }

  val saveGeminiKeyRoute =
    Endpoints.saveGeminiKeyEndpoint.serverLogic[Identity] { case (sessionCookieOpt, req) =>
      authenticate(sessionCookieOpt).flatMap { user =>
        gemini.validateKey(req.apiKey) match
          case Left(err) =>
            Left((StatusCode.BadRequest, s"Invalid Gemini API key: $err"))
          case Right(()) =>
            val encrypted = CryptoUtils.encrypt(req.apiKey.trim, authConfig.sessionSecret)
            userRepo.updateGeminiKey(user.id, encrypted)
            Right(GeminiKeyStatus(hasKey = true, maskedKey = Some(CryptoUtils.maskKey(req.apiKey.trim))))
      }
    }

  val deleteGeminiKeyRoute =
    Endpoints.deleteGeminiKeyEndpoint.serverLogic[Identity] { sessionCookieOpt =>
      authenticate(sessionCookieOpt).map { user =>
        userRepo.clearGeminiKey(user.id)
        "Gemini API key deleted successfully"
      }
    }

  val analyzeMealRoute: ServerEndpoint[Any, Identity] =
    Endpoints.analyzeMealEndpoint.serverLogicSuccess[Identity] { req =>
      mealService.analyze(req)
    }

  val createMealRoute: ServerEndpoint[Any, Identity] =
    Endpoints.createMealEndpoint.serverLogicSuccess[Identity] { req =>
      mealService.createMeal(defaultUserId, req)
    }

  val listMealsRoute: ServerEndpoint[Any, Identity] =
    Endpoints.listMealsEndpoint.serverLogicSuccess[Identity] { dateStr =>
      val date = Try(LocalDate.parse(dateStr)).getOrElse(LocalDate.now())
      mealService.listMeals(defaultUserId, date)
    }

  val deleteMealRoute: ServerEndpoint[Any, Identity] =
    Endpoints.deleteMealEndpoint.serverLogicSuccess[Identity] { idStr =>
      Try(UUID.fromString(idStr)).toOption match
        case Some(mealId) =>
          if mealService.deleteMeal(defaultUserId, mealId) then s"Meal $idStr deleted"
          else s"Meal $idStr not found"
        case None =>
          s"Invalid meal ID $idStr"
    }

  val getDailyCaloriesRoute: ServerEndpoint[Any, Identity] =
    Endpoints.getDailyCaloriesEndpoint.serverLogicSuccess[Identity] { dateStr =>
      val date = Try(LocalDate.parse(dateStr)).getOrElse(LocalDate.now())
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
      val fromDate = Try(LocalDate.parse(fromStr)).getOrElse(LocalDate.now().minusDays(30))
      val toDate   = Try(LocalDate.parse(toStr)).getOrElse(LocalDate.now())
      weightService.getWeights(defaultUserId, fromDate, toDate)
    }

  val indexRoute: ServerEndpoint[Any, Identity] =
    sttp.tapir.endpoint.get
      .in(sttp.tapir.stringToPath(""))
      .out(sttp.tapir.htmlBodyUtf8)
      .summary("Serve Single Page Application index.html")
      .serverLogicSuccess[Identity] { _ =>
        Option(getClass.getClassLoader.getResourceAsStream("webapp/index.html")) match
          case Some(is) =>
            Using.resource(is) { stream =>
              new String(stream.readAllBytes(), StandardCharsets.UTF_8)
            }
          case None =>
            "<!DOCTYPE html><html><body><div id='app'>CalTrack AI</div></body></html>"
      }

  val assetsRoute: ServerEndpoint[Any, Identity] =
    sttp.tapir.endpoint.get
      .in("assets" / sttp.tapir.paths)
      .out(sttp.tapir.byteArrayBody)
      .out(sttp.tapir.header[String]("Content-Type"))
      .summary("Serve static web assets")
      .serverLogicSuccess[Identity] { pathList =>
        val relativePath = pathList.mkString("/")
        val resourcePath = s"webapp/assets/$relativePath"
        Option(getClass.getClassLoader.getResourceAsStream(resourcePath)) match
          case Some(is) =>
            Using.resource(is) { stream =>
              val bytes = stream.readAllBytes()
              val contentType =
                if relativePath.endsWith(".js") then "application/javascript"
                else if relativePath.endsWith(".css") then "text/css"
                else if relativePath.endsWith(".html") then "text/html"
                else if relativePath.endsWith(".json") then "application/json"
                else "application/octet-stream"
              (bytes, contentType)
            }
          case None =>
            (Array.emptyByteArray, "application/octet-stream")
      }

  val healthRoute: ServerEndpoint[Any, Identity] =
    sttp.tapir.endpoint.get
      .in("api" / "health")
      .out(sttp.tapir.stringBody)
      .summary("Healthcheck endpoint")
      .serverLogicSuccess[Identity](_ => """{"status":"ok"}""")

  val allRoutes: List[ServerEndpoint[Any, Identity]] = List(
    healthRoute,
    assetsRoute,
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
    getWeightsRoute,
    getGeminiKeyStatusRoute,
    saveGeminiKeyRoute,
    deleteGeminiKeyRoute,
    indexRoute
  )

  def createServer(host: String = "0.0.0.0", port: Int = 8080): NettySyncServer =
    NettySyncServer()
      .host(host)
      .port(port)
      .addEndpoints(allRoutes)
