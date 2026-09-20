package com.calcul.auth

import munit.FunSuite
import java.util.UUID
import com.calcul.db.{TestPostgresContainer, UserRepository}

class AuthServiceSuite extends FunSuite:

  test("AuthService generates valid Google OAuth URL and signs/verifies sessions") {
    TestPostgresContainer.clearData()
    val conn = TestPostgresContainer.newConnection()
    try
      val userRepo = new UserRepository(conn)
      val config = AuthConfig(
        clientId = "google-client-id-123.apps.googleusercontent.com",
        clientSecret = "secret-xyz",
        redirectUri = "http://localhost:8080/api/auth/callback",
        sessionSecret = "super-secret-key-that-is-at-least-32-chars-long"
      )
      val authService = new AuthService(userRepo, config)

      // 1. Google OAuth URL
      val url = authService.loginUrl("state-abc")
      assert(url.contains("accounts.google.com"), "URL should point to Google accounts")
      assert(url.contains(config.clientId), "URL should contain clientId")
      assert(url.contains("scope=openid+email+profile") || url.contains("scope=openid"), "URL should contain scope")

      // 2. Session signing and verification
      val userId = UUID.randomUUID()
      val token  = authService.createSessionToken(userId)
      assert(token.nonEmpty, "Session token should not be empty")

      val verifiedUserId = authService.verifySessionToken(token)
      assertEquals(verifiedUserId, Some(userId), "Session should verify to original user ID")

      // Tampered token should fail
      val tampered = token + "invalid"
      assert(authService.verifySessionToken(tampered).isEmpty, "Tampered token should fail verification")

      // 3. Mock Google callback handling
      val user = authService.handleGoogleUser(
        googleId = "g-user-999",
        email = "user999@example.com",
        name = "Google User",
        pictureUrl = Some("https://example.com/p.jpg")
      )
      assertEquals(user.email, "user999@example.com")
      assertEquals(user.name, "Google User")

      val fromDb = userRepo.findByGoogleId("g-user-999")
      assert(fromDb.isDefined, "User should be persisted in DB")
      assertEquals(fromDb.get.id, user.id)
    finally conn.close()
  }
