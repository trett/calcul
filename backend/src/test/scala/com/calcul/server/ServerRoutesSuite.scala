package com.calcul.server

import munit.FunSuite
import com.calcul.db.TestPostgresContainer

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
      val getResult = routes.getGeminiKeyStatusRoute.logic(sttp.monad.IdentityMonad)(())(None)
      assertEquals(getResult, Left((sttp.model.StatusCode.Unauthorized, "Unauthorized")))

      val saveResult = routes.saveGeminiKeyRoute.logic(sttp.monad.IdentityMonad)(())(
        (None, com.calcul.model.SaveGeminiKeyRequest("test-key"))
      )
      assertEquals(saveResult, Left((sttp.model.StatusCode.Unauthorized, "Unauthorized")))

      val deleteResult = routes.deleteGeminiKeyRoute.logic(sttp.monad.IdentityMonad)(())(None)
      assertEquals(deleteResult, Left((sttp.model.StatusCode.Unauthorized, "Unauthorized")))

      val meResult = routes.meRoute.logic(sttp.monad.IdentityMonad)(())(None)
      assertEquals(meResult, Left((sttp.model.StatusCode.Unauthorized, "Unauthorized")))
    finally conn.close()
  }

  test("Settings routes allow authenticated user to manage key") {
    val conn = TestPostgresContainer.newConnection()
    try
      val mockGemini = new com.calcul.ai.GeminiService:
        override def validateKey(key: String): Either[String, Unit] =
          if key == "valid-secret-key-1234" then Right(())
          else Left("Bad key")
      val routes = new ServerRoutes(
        transactor = com.calcul.db.DbTransactor.fromConnection(conn),
        gemini = mockGemini
      )
      val user = com.calcul.model.User(
        id = java.util.UUID.randomUUID(),
        googleId = "g-routes-test",
        email = "routes@test.com",
        name = "Routes User",
        pictureUrl = None,
        createdAt = java.time.Instant.now()
      )
      routes.userRepo.upsert(user)
      val token = routes.authService.createSessionToken(user.id)

      // Initially no key
      val getRes1 = routes.getGeminiKeyStatusRoute.logic(sttp.monad.IdentityMonad)(())(Some(token))
      assertEquals(getRes1, Right(com.calcul.model.GeminiKeyStatus(hasKey = false, maskedKey = None)))

      val meRes1 = routes.meRoute.logic(sttp.monad.IdentityMonad)(())(Some(token))
      assertEquals(meRes1.map(_.hasGeminiKey), Right(false))
      assertEquals(meRes1.map(_.maskedGeminiKey), Right(None))

      // Empty/invalid key fails validation
      val emptyRes = routes.saveGeminiKeyRoute.logic(sttp.monad.IdentityMonad)(())(
        (Some(token), com.calcul.model.SaveGeminiKeyRequest("invalid"))
      )
      assert(emptyRes.isLeft)

      // Valid key succeeds
      val saveRes = routes.saveGeminiKeyRoute.logic(sttp.monad.IdentityMonad)(())(
        (Some(token), com.calcul.model.SaveGeminiKeyRequest("valid-secret-key-1234"))
      )
      assert(saveRes.isRight)
      assertEquals(saveRes.map(_.hasKey), Right(true))
      assertEquals(saveRes.map(_.maskedKey), Right(Some("••••••••••••1234")))

      // Key status now reflects the saved key
      val getRes2 = routes.getGeminiKeyStatusRoute.logic(sttp.monad.IdentityMonad)(())(Some(token))
      assertEquals(
        getRes2,
        Right(com.calcul.model.GeminiKeyStatus(hasKey = true, maskedKey = Some("••••••••••••1234")))
      )

      val meRes2 = routes.meRoute.logic(sttp.monad.IdentityMonad)(())(Some(token))
      assertEquals(meRes2.map(_.hasGeminiKey), Right(true))
      assertEquals(meRes2.map(_.maskedGeminiKey), Right(Some("••••••••••••1234")))

      // Delete key succeeds
      val delRes = routes.deleteGeminiKeyRoute.logic(sttp.monad.IdentityMonad)(())(Some(token))
      assert(delRes.isRight)

      // Key status now shows no key
      val getRes3 = routes.getGeminiKeyStatusRoute.logic(sttp.monad.IdentityMonad)(())(Some(token))
      assertEquals(getRes3, Right(com.calcul.model.GeminiKeyStatus(hasKey = false, maskedKey = None)))
    finally conn.close()
  }

  test("callbackRoute and legacyCallbackRoute handle OAuth code exchange") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)

      // Test standard callback endpoint (/api/auth/callback) with mock code
      val res1 = routes.callbackRoute.logic(sttp.monad.IdentityMonad)(())("mock-code-123")
      assert(res1.isRight, "Callback route should succeed for mock code")
      val (status1, locOpt1, cookieOpt1, html1) = res1.toOption.get
      assertEquals(status1, sttp.model.StatusCode.Found)
      assertEquals(locOpt1, Some("/"))
      assert(cookieOpt1.isDefined, "Session cookie header should be set")
      assert(cookieOpt1.get.contains("session="), "Cookie should contain session token")
      assert(html1.contains("Logging in"), "Should return redirect HTML")

      // Test legacy callback endpoint (/auth/callback) with mock code
      val res2 = routes.legacyCallbackRoute.logic(sttp.monad.IdentityMonad)(())("mock-code-456")
      assert(res2.isRight, "Legacy callback route should succeed for mock code")
      val (status2, locOpt2, cookieOpt2, html2) = res2.toOption.get
      assertEquals(status2, sttp.model.StatusCode.Found)
      assertEquals(locOpt2, Some("/"))
      assert(cookieOpt2.isDefined, "Session cookie header should be set on legacy route")
      assert(cookieOpt2.get.contains("session="), "Cookie should contain session token")
      assert(html2.contains("Logging in"), "Should return redirect HTML")
    finally conn.close()
  }

  test("Meal, calorie, and weight routes reject unauthenticated requests with 401 Unauthorized") {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      val date   = java.time.LocalDate.parse("2026-09-20")

      val mealReq = com.calcul.model.CreateMealRequest(
        mealDate = date,
        description = "Test meal",
        totalCalories = 500,
        aiExplanation = "Eggs",
        items = Nil
      )
      val targetReq = com.calcul.model.SetTargetRequest(date, 2000)
      val weightReq = com.calcul.model.RecordWeightRequest(date, BigDecimal("75.0"), "kg")

      assertEquals(
        routes.createMealRoute.logic(sttp.monad.IdentityMonad)(())((None, mealReq)),
        Left((sttp.model.StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.listMealsRoute.logic(sttp.monad.IdentityMonad)(())((None, "2026-09-20")),
        Left((sttp.model.StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.deleteMealRoute.logic(sttp.monad.IdentityMonad)(())((None, java.util.UUID.randomUUID().toString)),
        Left((sttp.model.StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.getDailyCaloriesRoute.logic(sttp.monad.IdentityMonad)(())((None, "2026-09-20")),
        Left((sttp.model.StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.setDailyTargetRoute.logic(sttp.monad.IdentityMonad)(())((None, targetReq)),
        Left((sttp.model.StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.recordWeightRoute.logic(sttp.monad.IdentityMonad)(())((None, weightReq)),
        Left((sttp.model.StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.getWeightsRoute.logic(sttp.monad.IdentityMonad)(())((None, "2026-09-01", "2026-09-20")),
        Left((sttp.model.StatusCode.Unauthorized, "Unauthorized"))
      )
    finally conn.close()
  }

  test(
    "Authenticated user can set target, log meals, check daily calories, and track weights without foreign key violations"
  ) {
    val conn = TestPostgresContainer.newConnection()
    try
      val routes = new ServerRoutes(conn)
      val user = com.calcul.model.User(
        id = java.util.UUID.randomUUID(),
        googleId = "g-data-test-user",
        email = "datatest@test.com",
        name = "Data Test User",
        pictureUrl = None,
        createdAt = java.time.Instant.now()
      )
      routes.userRepo.upsert(user)
      val token = routes.authService.createSessionToken(user.id)
      val date  = java.time.LocalDate.parse("2026-09-20")

      // 1. Set daily target for authenticated user (should not violate foreign key constraint)
      val targetRes = routes.setDailyTargetRoute.logic(sttp.monad.IdentityMonad)(())(
        (Some(token), com.calcul.model.SetTargetRequest(date, 2400))
      )
      assert(targetRes.isRight, s"Setting target failed: $targetRes")
      val target = targetRes.toOption.get
      assertEquals(target.userId, user.id)
      assertEquals(target.calorieTarget, 2400)

      // 2. Create meal for authenticated user
      val mealReq = com.calcul.model.CreateMealRequest(
        mealDate = date,
        description = "Avocado Toast & Coffee",
        totalCalories = 450,
        aiExplanation = "Toast with avocado and black coffee",
        items = List(
          com.calcul.model.CreateMealItem("Avocado Toast", 445),
          com.calcul.model.CreateMealItem("Black Coffee", 5)
        )
      )
      val createMealRes = routes.createMealRoute.logic(sttp.monad.IdentityMonad)(())((Some(token), mealReq))
      assert(createMealRes.isRight, s"Create meal failed: $createMealRes")
      val meal = createMealRes.toOption.get
      assertEquals(meal.userId, user.id)
      assertEquals(meal.totalCalories, 450)

      // 3. List meals for authenticated user
      val listRes = routes.listMealsRoute.logic(sttp.monad.IdentityMonad)(())((Some(token), "2026-09-20"))
      assert(listRes.isRight)
      assertEquals(listRes.toOption.get.size, 1)
      assertEquals(listRes.toOption.get.head.id, meal.id)

      // 4. Daily calories summary incorporates target and meal
      val calRes = routes.getDailyCaloriesRoute.logic(sttp.monad.IdentityMonad)(())((Some(token), "2026-09-20"))
      assert(calRes.isRight)
      val summary = calRes.toOption.get
      assertEquals(summary.calorieTarget, 2400)
      assertEquals(summary.totalConsumed, 450)
      assertEquals(summary.remainingCalories, 1950)
      assertEquals(summary.mealsCount, 1)

      // 5. Record weight
      val weightReq = com.calcul.model.RecordWeightRequest(date, BigDecimal("76.80"), "kg")
      val weightRes = routes.recordWeightRoute.logic(sttp.monad.IdentityMonad)(())((Some(token), weightReq))
      assert(weightRes.isRight)
      val recordedWeight = weightRes.toOption.get
      assertEquals(recordedWeight.userId, user.id)
      assertEquals(recordedWeight.weight, BigDecimal("76.80"))

      // 6. Get weights history
      val getWeightsRes = routes.getWeightsRoute.logic(sttp.monad.IdentityMonad)(())(
        (Some(token), "2026-09-01", "2026-09-21")
      )
      assert(getWeightsRes.isRight)
      assertEquals(getWeightsRes.toOption.get.size, 1)

      // 7. Delete meal
      val delMealRes = routes.deleteMealRoute.logic(sttp.monad.IdentityMonad)(())((Some(token), meal.id.toString))
      assert(delMealRes.isRight)
      val listAfterDel = routes.listMealsRoute.logic(sttp.monad.IdentityMonad)(())((Some(token), "2026-09-20"))
      assertEquals(listAfterDel.toOption.get.size, 0)
    finally conn.close()
  }
