package ru.trett.calcul.server

import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import scala.util.{Try, Using}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.stringToPath
import ru.trett.calcul.ai.GeminiService
import ru.trett.calcul.api.Endpoints
import ru.trett.calcul.auth.{AuthConfig, AuthService, CryptoUtils}
import ru.trett.calcul.db.*
import ru.trett.calcul.model.{GeminiKeyStatus, User, UserSummary}

class ServerRoutes(
    transactor: DbTransactor,
    gemini: GeminiService = new GeminiService(),
    authConfig: AuthConfig = AuthConfig.fromEnv()
):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds), new GeminiService(), AuthConfig.fromEnv())
  def this(ds: DataSource, gemini: GeminiService, authConfig: AuthConfig) =
    this(DbTransactor.fromDataSource(ds), gemini, authConfig)
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn), new GeminiService(), AuthConfig.fromEnv())
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

  private val logger = LoggerFactory.getLogger(getClass)

  private def authenticate(sessionCookieOpt: Option[String]): Either[(StatusCode, String), User] =
    sessionCookieOpt.flatMap(authService.verifySessionToken).flatMap(userRepo.findById) match
      case Some(user) => Right(user)
      case None       => Left((StatusCode.Unauthorized, "Unauthorized"))

  val loginRoute: ServerEndpoint[Any, Identity] =
    Endpoints.loginEndpoint.serverLogicSuccess[Identity](_ => authService.loginUrl("auth_state"))

  private def handleCallback(code: String): (StatusCode, Option[String], Option[String]) =
    Try {
      val googleUserOpt = authService.exchangeGoogleCode(code)
      val userSummaryOpt = googleUserOpt match
        case Some(gu) =>
          Some(authService.handleGoogleUser(gu.googleId, gu.email, gu.name, gu.pictureUrl))
        case None =>
          if authConfig.clientId.startsWith("mock-") || authConfig.clientSecret.startsWith("mock-") then
            Some(authService.handleGoogleUser("google-demo-user", "user@example.com", "Demo User", None))
          else None

      userSummaryOpt match
        case Some(userSummary) =>
          val sessionToken = authService.createSessionToken(userSummary.id)
          val cookieHeader = s"session=$sessionToken; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000"
          (StatusCode.Found, Some("/"), Some(cookieHeader))
        case None =>
          logger.error("Authentication failed: unable to obtain user profile from Google OAuth code")
          (StatusCode.Found, Some("/?auth_error=google_failed"), None)
    } match
      case scala.util.Success(res) => res
      case scala.util.Failure(ex) =>
        logger.error(s"Unexpected error during Google OAuth callback processing", ex)
        (StatusCode.Found, Some("/?auth_error=server_error"), None)

  val callbackRoute =
    Endpoints.callbackEndpoint.serverLogicSuccess[Identity](handleCallback)

  val legacyCallbackRoute =
    sttp.tapir.endpoint.get
      .in("auth" / "callback")
      .in(sttp.tapir.query[String]("code"))
      .out(sttp.tapir.statusCode)
      .out(sttp.tapir.header[Option[String]]("Location"))
      .out(sttp.tapir.header[Option[String]]("Set-Cookie"))
      .summary("Legacy /auth/callback alias for Google OAuth2")
      .serverLogicSuccess[Identity](handleCallback)

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
        val cleanedKey = req.apiKey.trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'").trim
        gemini.validateKey(cleanedKey) match
          case Left(err) =>
            logger.warn(s"Gemini API key validation rejected for user ${user.id}: $err")
            Left((StatusCode.BadRequest, s"Invalid Gemini API key: $err"))
          case Right(()) =>
            val encrypted = CryptoUtils.encrypt(cleanedKey, authConfig.sessionSecret)
            userRepo.updateGeminiKey(user.id, encrypted)
            logger.info(s"Gemini API key successfully encrypted and saved for user ${user.id}")
            Right(GeminiKeyStatus(hasKey = true, maskedKey = Some(CryptoUtils.maskKey(cleanedKey))))
      }
    }

  val deleteGeminiKeyRoute =
    Endpoints.deleteGeminiKeyEndpoint.serverLogic[Identity] { sessionCookieOpt =>
      authenticate(sessionCookieOpt).map { user =>
        userRepo.clearGeminiKey(user.id)
        "Gemini API key deleted successfully"
      }
    }

  val analyzeMealRoute =
    Endpoints.analyzeMealEndpoint.serverLogic[Identity] { case (sessionCookieOpt, req) =>
      authenticate(sessionCookieOpt).flatMap { user =>
        userRepo.getEncryptedGeminiKey(user.id) match
          case None =>
            Left((StatusCode.BadRequest, "Gemini API key not configured. Please configure your key in User Settings."))
          case Some(encKey) =>
            CryptoUtils.decrypt(encKey, authConfig.sessionSecret) match
              case Left(err) =>
                Left((StatusCode.InternalServerError, s"Failed to decrypt Gemini API key: $err"))
              case Right(key) =>
                Right(mealService.analyze(req, userApiKey = Some(key)))
      }
    }

  val createMealRoute =
    Endpoints.createMealEndpoint.serverLogic[Identity] { case (sessionCookieOpt, req) =>
      authenticate(sessionCookieOpt).map { user =>
        mealService.createMeal(user.id, req)
      }
    }

  val listMealsRoute =
    Endpoints.listMealsEndpoint.serverLogic[Identity] { case (sessionCookieOpt, dateStr) =>
      authenticate(sessionCookieOpt).map { user =>
        val date = Try(LocalDate.parse(dateStr)).getOrElse(LocalDate.now())
        mealService.listMeals(user.id, date)
      }
    }

  val deleteMealRoute =
    Endpoints.deleteMealEndpoint.serverLogic[Identity] { case (sessionCookieOpt, idStr) =>
      authenticate(sessionCookieOpt).flatMap { user =>
        Try(UUID.fromString(idStr)).toOption match
          case Some(mealId) =>
            if mealService.deleteMeal(user.id, mealId) then Right(s"Meal $idStr deleted")
            else Left((StatusCode.NotFound, s"Meal $idStr not found"))
          case None =>
            Left((StatusCode.BadRequest, s"Invalid meal ID $idStr"))
      }
    }

  val getDailyCaloriesRoute =
    Endpoints.getDailyCaloriesEndpoint.serverLogic[Identity] { case (sessionCookieOpt, dateStr) =>
      authenticate(sessionCookieOpt).map { user =>
        val date = Try(LocalDate.parse(dateStr)).getOrElse(LocalDate.now())
        calorieService.getDailySummary(user.id, date)
      }
    }

  val setDailyTargetRoute =
    Endpoints.setDailyTargetEndpoint.serverLogic[Identity] { case (sessionCookieOpt, req) =>
      authenticate(sessionCookieOpt).map { user =>
        calorieService.setTarget(user.id, req)
      }
    }

  val recordWeightRoute =
    Endpoints.recordWeightEndpoint.serverLogic[Identity] { case (sessionCookieOpt, req) =>
      authenticate(sessionCookieOpt).map { user =>
        weightService.recordWeight(user.id, req)
      }
    }

  val getWeightsRoute =
    Endpoints.getWeightsEndpoint.serverLogic[Identity] { case (sessionCookieOpt, fromStr, toStr) =>
      authenticate(sessionCookieOpt).map { user =>
        val fromDate = Try(LocalDate.parse(fromStr)).getOrElse(LocalDate.now().minusDays(30))
        val toDate   = Try(LocalDate.parse(toStr)).getOrElse(LocalDate.now())
        weightService.getWeights(user.id, fromDate, toDate)
      }
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
            logger.warn("webapp/index.html not found on classpath")
            ""
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
    legacyCallbackRoute,
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
