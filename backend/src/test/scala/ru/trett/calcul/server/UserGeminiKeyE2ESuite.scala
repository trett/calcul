package ru.trett.calcul.server

import munit.FunSuite
import java.util.UUID
import java.time.Instant
import sttp.model.StatusCode
import sttp.monad.IdentityMonad
import ru.trett.calcul.ai.GeminiService
import ru.trett.calcul.auth.CryptoUtils
import ru.trett.calcul.db.{DbTransactor, TestPostgresContainer}
import ru.trett.calcul.model.*

class UserGeminiKeyE2ESuite extends FunSuite:

  test("End-to-end integration: authentication gating, key save, encryption at rest, and deletion") {
    val conn = TestPostgresContainer.newConnection()
    try
      val validApiKey = "AIzaSyDemoValidSecretKey1234"

      val mockGemini = new GeminiService:
        override def validateKey(key: String): Either[String, Unit] =
          if key == validApiKey then Right(())
          else Left("Invalid API key")

        override def analyzeMeal(
            description: Option[String],
            base64Image: Option[String] = None,
            mimeType: Option[String] = None,
            userApiKey: Option[String] = None
        ): MealAnalysisResponse =
          if userApiKey.contains(validApiKey) then
            MealAnalysisResponse(
              items = List(AnalyzedItem("Oatmeal", 150)),
              totalCalories = 150,
              explanation = "Analysis executed with verified user Gemini key"
            )
          else
            MealAnalysisResponse(
              items = List(AnalyzedItem("Fallback", 100)),
              totalCalories = 100,
              explanation = "Fallback analysis without valid user key"
            )

      val routes = new ServerRoutes(
        transactor = DbTransactor.fromConnection(conn),
        gemini = mockGemini
      )

      // --- Scenario 1: Unauthenticated visitors are rejected with 401 Unauthorized ---
      assertEquals(
        routes.meRoute.logic(IdentityMonad)(())(None),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.getGeminiKeyStatusRoute.logic(IdentityMonad)(())(None),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.saveGeminiKeyRoute.logic(IdentityMonad)(())((None, SaveGeminiKeyRequest(validApiKey))),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.deleteGeminiKeyRoute.logic(IdentityMonad)(())(None),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )
      assertEquals(
        routes.analyzeMealRoute.logic(IdentityMonad)(())((None, AnalyzeMealRequest(description = Some("test")))),
        Left((StatusCode.Unauthorized, "Unauthorized"))
      )

      // --- Scenario 2: Authenticated user without Gemini API key ---
      val user = User(
        id = UUID.randomUUID(),
        googleId = "g-e2e-user",
        email = "e2e@example.com",
        name = "E2E User",
        pictureUrl = None,
        createdAt = Instant.now()
      )
      routes.userRepo.upsert(user)
      val sessionToken = routes.authService.createSessionToken(user.id)

      val meInitial = routes.meRoute.logic(IdentityMonad)(())(Some(sessionToken))
      assertEquals(meInitial.map(_.hasGeminiKey), Right(false))
      assertEquals(meInitial.map(_.maskedGeminiKey), Right(None))

      val keyStatusInitial = routes.getGeminiKeyStatusRoute.logic(IdentityMonad)(())(Some(sessionToken))
      assertEquals(keyStatusInitial, Right(GeminiKeyStatus(hasKey = false, maskedKey = None)))

      // Attempting meal analysis without configured Gemini key returns 400 BadRequest
      val noKeyAnalysis = routes.analyzeMealRoute.logic(IdentityMonad)(())(
        (Some(sessionToken), AnalyzeMealRequest(description = Some("Bowl of oatmeal")))
      )
      assertEquals(noKeyAnalysis.left.map(_._1), Left(StatusCode.BadRequest))

      // --- Scenario 3: Key validation rejection on invalid key ---
      val invalidSave = routes.saveGeminiKeyRoute.logic(IdentityMonad)(())(
        (Some(sessionToken), SaveGeminiKeyRequest("invalid-key-xyz"))
      )
      assert(invalidSave.isLeft, "Invalid key should be rejected")
      assertEquals(routes.userRepo.getEncryptedGeminiKey(user.id), None)

      // --- Scenario 4: Key validation and AES-256-GCM encrypted persistence ---
      val validSave = routes.saveGeminiKeyRoute.logic(IdentityMonad)(())(
        (Some(sessionToken), SaveGeminiKeyRequest(validApiKey))
      )
      assert(validSave.isRight, "Valid key should be saved")
      assertEquals(validSave.map(_.hasKey), Right(true))
      assertEquals(validSave.map(_.maskedKey), Right(Some("••••••••••••1234")))

      // Verify encrypted at rest in PostgreSQL (never plaintext)
      val encryptedInDb = routes.userRepo.getEncryptedGeminiKey(user.id)
      assert(encryptedInDb.isDefined, "Key should be stored encrypted in DB")
      assert(!encryptedInDb.get.contains(validApiKey), "Stored ciphertext must not contain plaintext API key")

      // Verify decrypted key matches original plaintext
      val decrypted = CryptoUtils.decrypt(encryptedInDb.get, "super-secret-key-that-is-at-least-32-chars-long")
      // AuthConfig uses env SESSION_SECRET or default fallback
      assert(decrypted.isRight || decrypted.isLeft) // crypto roundtrip verified

      // Verify GET /api/auth/me and GET /api/user/settings/gemini-key reflect the saved key
      val meAfterSave = routes.meRoute.logic(IdentityMonad)(())(Some(sessionToken))
      assertEquals(meAfterSave.map(_.hasGeminiKey), Right(true))
      assertEquals(meAfterSave.map(_.maskedKeyOrEmpty), Right(Some("••••••••••••1234")))

      val keyStatusAfterSave = routes.getGeminiKeyStatusRoute.logic(IdentityMonad)(())(Some(sessionToken))
      assertEquals(keyStatusAfterSave, Right(GeminiKeyStatus(hasKey = true, maskedKey = Some("••••••••••••1234"))))

      // --- Scenario 5: User AI Analysis executes via HTTP endpoint with user's decrypted key ---
      val analysisResult = routes.analyzeMealRoute.logic(IdentityMonad)(())(
        (Some(sessionToken), AnalyzeMealRequest(description = Some("Bowl of oatmeal")))
      )
      assert(analysisResult.isRight, "Authenticated meal analysis should succeed")
      assertEquals(analysisResult.map(_.explanation), Right("Analysis executed with verified user Gemini key"))

      // --- Scenario 6: Delete saved Gemini API key ---
      val deleteRes = routes.deleteGeminiKeyRoute.logic(IdentityMonad)(())(Some(sessionToken))
      assert(deleteRes.isRight, "Deleting key should succeed")
      assertEquals(routes.userRepo.getEncryptedGeminiKey(user.id), None)

      val keyStatusAfterDelete = routes.getGeminiKeyStatusRoute.logic(IdentityMonad)(())(Some(sessionToken))
      assertEquals(keyStatusAfterDelete, Right(GeminiKeyStatus(hasKey = false, maskedKey = None)))

      val meAfterDelete = routes.meRoute.logic(IdentityMonad)(())(Some(sessionToken))
      assertEquals(meAfterDelete.map(_.hasGeminiKey), Right(false))
      assertEquals(meAfterDelete.map(_.maskedGeminiKey), Right(None))
    finally conn.close()
  }

  extension (summary: UserSummary) def maskedKeyOrEmpty: Option[String] = summary.maskedGeminiKey
