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
