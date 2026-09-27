package ru.trett.calcul.server

import java.time.{Instant, LocalDate}
import java.util.UUID
import munit.FunSuite
import sttp.model.StatusCode
import sttp.monad.IdentityMonad
import ru.trett.calcul.ai.GeminiService
import ru.trett.calcul.db.{DbTransactor, TestPostgresContainer}
import ru.trett.calcul.model.*

class ServerRoutesSuite extends FunSuite:

  test("ServerRoutes initializes routes and NettySyncServer") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      assertEquals(routes.allRoutes.size, 19)
      val server = routes.createServer(port = 8089)
      assert(Option(server).isDefined, "Server should be initialized")
    finally conn.close()
  }

  test("Settings routes reject unauthenticated requests with 401 Unauthorized") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes    = new ServerRoutes(conn)
      val getResult = routes.getGeminiKeyStatusRoute.logic(IdentityMonad)(())(None)
      assertEquals(getResult, Left((StatusCode.Unauthorized, "Unauthorized")))

      val saveResult = routes.saveGeminiKeyRoute.logic(IdentityMonad)(())(
        (None, SaveGeminiKeyRequest("test-key"))
      )
      assertEquals(saveResult, Left((StatusCode.Unauthorized, "Unauthorized")))

      val deleteResult = routes.deleteGeminiKeyRoute.logic(IdentityMonad)(())(None)
      assertEquals(deleteResult, Left((StatusCode.Unauthorized, "Unauthorized")))

      val meResult = routes.meRoute.logic(IdentityMonad)(())(None)
      assertEquals(meResult, Left((StatusCode.Unauthorized, "Unauthorized")))
    finally conn.close()
  }

  test("Settings routes allow authenticated user to manage key") {
    val conn = TestPostgresContainer.newConnection()
    try
      val mockGemini = new GeminiService:
        override def validateKey(key: String): Either[String, Unit] =
          if key == "valid-secret-key-1234" then Right(())
          else Left("Bad key")
      val routes = new ServerRoutes(
        transactor = DbTransactor.fromConnection(conn),
        gemini = mockGemini
      )
      val user = User(
        id = UUID.randomUUID(),
        googleId = "g-routes-test",
        email = "routes@test.com",
        name = "Routes User",
        pictureUrl = None,
        createdAt = Instant.now()
      )
      routes.userRepo.upsert(user)
      val token = routes.authService.createSessionToken(user.id)

      // Initially no key
      val getRes1 = routes.getGeminiKeyStatusRoute.logic(IdentityMonad)(())(Some(token))
      assertEquals(getRes1, Right(GeminiKeyStatus(hasKey = false, maskedKey = None)))

      val meRes1 = routes.meRoute.logic(IdentityMonad)(())(Some(token))
      assertEquals(meRes1.map(_.hasGeminiKey), Right(false))
      assertEquals(meRes1.map(_.maskedGeminiKey), Right(None))

      // Empty/invalid key fails validation
      val emptyRes = routes.saveGeminiKeyRoute.logic(IdentityMonad)(())(
        (Some(token), SaveGeminiKeyRequest("invalid"))
      )
      assert(emptyRes.isLeft)

      // Valid key succeeds
      val saveRes = routes.saveGeminiKeyRoute.logic(IdentityMonad)(())(
        (Some(token), SaveGeminiKeyRequest("valid-secret-key-1234"))
      )
      assert(saveRes.isRight)
      assertEquals(saveRes.map(_.hasKey), Right(true))
      assertEquals(saveRes.map(_.maskedKey), Right(Some("••••••••••••1234")))

      // Key status now reflects the saved key
      val getRes2 = routes.getGeminiKeyStatusRoute.logic(IdentityMonad)(())(Some(token))
      assertEquals(
        getRes2,
        Right(GeminiKeyStatus(hasKey = true, maskedKey = Some("••••••••••••1234")))
      )

      val meRes2 = routes.meRoute.logic(IdentityMonad)(())(Some(token))
      assertEquals(meRes2.map(_.hasGeminiKey), Right(true))
      assertEquals(meRes2.map(_.maskedGeminiKey), Right(Some("••••••••••••1234")))

      // Delete key succeeds
      val delRes = routes.deleteGeminiKeyRoute.logic(IdentityMonad)(())(Some(token))
      assert(delRes.isRight)

      // Key status now shows no key
      val getRes3 = routes.getGeminiKeyStatusRoute.logic(IdentityMonad)(())(Some(token))
      assertEquals(getRes3, Right(GeminiKeyStatus(hasKey = false, maskedKey = None)))
    finally conn.close()
  }

  test("callbackRoute and legacyCallbackRoute handle OAuth code exchange") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)

      // Test standard callback endpoint (/api/auth/callback) with mock code
      val res1 = routes.callbackRoute.logic(IdentityMonad)(())("mock-code-123")
      assert(res1.isRight, "Callback route should succeed for mock code")
      val (status1, locOpt1, cookieOpt1) = res1.toOption.get
      assertEquals(status1, StatusCode.Found)
      assertEquals(locOpt1, Some("/"))
      assert(cookieOpt1.isDefined, "Session cookie header should be set")
      assert(cookieOpt1.get.contains("session="), "Cookie should contain session token")

      // Test legacy callback endpoint (/auth/callback) with mock code
      val res2 = routes.legacyCallbackRoute.logic(IdentityMonad)(())("mock-code-456")
      assert(res2.isRight, "Legacy callback route should succeed for mock code")
      val (status2, locOpt2, cookieOpt2) = res2.toOption.get
      assertEquals(status2, StatusCode.Found)
      assertEquals(locOpt2, Some("/"))
      assert(cookieOpt2.isDefined, "Session cookie header should be set on legacy route")
      assert(cookieOpt2.get.contains("session="), "Cookie should contain session token")
    finally conn.close()
  }

  test("Meal, calorie, and weight routes reject unauthenticated requests with 401 Unauthorized") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      val date   = LocalDate.parse("2026-09-20")

      val mealReq = CreateMealRequest(
        mealDate = date,
        description = "Test meal",
        totalCalories = 500,
        aiExplanation = "Eggs",
        items = Nil
      )
      val targetReq = SetTargetRequest(date, 2000)
      val weightReq = RecordWeightRequest(date, BigDecimal("75.0"), "kg")

      assertEquals(
        routes.createMealRoute.logic(IdentityMonad)(())((None, mealReq)),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.listMealsRoute.logic(IdentityMonad)(())((None, "2026-09-20")),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.deleteMealRoute.logic(IdentityMonad)(())((None, UUID.randomUUID().toString)),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.getDailyCaloriesRoute.logic(IdentityMonad)(())((None, "2026-09-20")),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.setDailyTargetRoute.logic(IdentityMonad)(())((None, targetReq)),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.recordWeightRoute.logic(IdentityMonad)(())((None, weightReq)),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.getWeightsRoute.logic(IdentityMonad)(())((None, "2026-09-01", "2026-09-20")),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
    finally conn.close()
  }

  test(
    "Authenticated user can set target, log meals, check daily calories, and track weights without foreign key violations"
  ) {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      val user = User(
        id = UUID.randomUUID(),
        googleId = "g-data-test-user",
        email = "datatest@test.com",
        name = "Data Test User",
        pictureUrl = None,
        createdAt = Instant.now()
      )
      routes.userRepo.upsert(user)
      val token = routes.authService.createSessionToken(user.id)
      val date  = LocalDate.parse("2026-09-20")

      // 1. Set daily target for authenticated user (should not violate foreign key constraint)
      val targetRes = routes.setDailyTargetRoute.logic(IdentityMonad)(())(
        (Some(token), SetTargetRequest(date, 2400))
      )
      assert(targetRes.isRight, s"Setting target failed: $targetRes")
      val target = targetRes.toOption.get
      assertEquals(target.userId, user.id)
      assertEquals(target.calorieTarget, 2400)

      // 2. Create meal for authenticated user
      val mealReq = CreateMealRequest(
        mealDate = date,
        description = "Avocado Toast & Coffee",
        totalCalories = 450,
        aiExplanation = "Toast with avocado and black coffee",
        items = List(
          CreateMealItem("Avocado Toast", 445),
          CreateMealItem("Black Coffee", 5)
        )
      )
      val createMealRes = routes.createMealRoute.logic(IdentityMonad)(())((Some(token), mealReq))
      assert(createMealRes.isRight, s"Create meal failed: $createMealRes")
      val meal = createMealRes.toOption.get
      assertEquals(meal.userId, user.id)
      assertEquals(meal.totalCalories, 450)

      // 3. List meals for authenticated user
      val listRes = routes.listMealsRoute.logic(IdentityMonad)(())((Some(token), "2026-09-20"))
      assert(listRes.isRight)
      assertEquals(listRes.toOption.get.size, 1)
      assertEquals(listRes.toOption.get.head.id, meal.id)

      // 4. Daily calories summary incorporates target and meal
      val calRes = routes.getDailyCaloriesRoute.logic(IdentityMonad)(())((Some(token), "2026-09-20"))
      assert(calRes.isRight)
      val summary = calRes.toOption.get
      assertEquals(summary.calorieTarget, 2400)
      assertEquals(summary.totalConsumed, 450)
      assertEquals(summary.remainingCalories, 1950)
      assertEquals(summary.mealsCount, 1)

      // 5. Record weight
      val weightReq = RecordWeightRequest(date, BigDecimal("76.80"), "kg")
      val weightRes = routes.recordWeightRoute.logic(IdentityMonad)(())((Some(token), weightReq))
      assert(weightRes.isRight)
      val recordedWeight = weightRes.toOption.get
      assertEquals(recordedWeight.userId, user.id)
      assertEquals(recordedWeight.weight, BigDecimal("76.80"))

      // 6. Get weights history
      val getWeightsRes = routes.getWeightsRoute.logic(IdentityMonad)(())(
        (Some(token), "2026-09-01", "2026-09-21")
      )
      assert(getWeightsRes.isRight)
      assertEquals(getWeightsRes.toOption.get.size, 1)

      // 7. Delete meal
      val delMealRes = routes.deleteMealRoute.logic(IdentityMonad)(())((Some(token), meal.id.toString))
      assert(delMealRes.isRight)
      val listAfterDel = routes.listMealsRoute.logic(IdentityMonad)(())((Some(token), "2026-09-20"))
      assertEquals(listAfterDel.toOption.get.size, 0)
    finally conn.close()
  }
