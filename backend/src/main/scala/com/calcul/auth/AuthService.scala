package com.calcul.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.{Base64, UUID}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.Try
import com.calcul.db.UserRepository
import com.calcul.model.{User, UserSummary}

final case class AuthConfig(
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    sessionSecret: String
)

object AuthConfig:
  def fromEnv(): AuthConfig =
    AuthConfig(
      clientId = sys.env.getOrElse("GOOGLE_CLIENT_ID", "mock-google-client-id"),
      clientSecret = sys.env.getOrElse("GOOGLE_CLIENT_SECRET", "mock-google-client-secret"),
      redirectUri = sys.env.getOrElse("GOOGLE_REDIRECT_URI", "http://localhost:8080/api/auth/callback"),
      sessionSecret = sys.env.getOrElse("SESSION_SECRET", "default-insecure-secret-key-at-least-32-chars")
    )

class AuthService(userRepo: UserRepository, config: AuthConfig):

  def loginUrl(state: String): String =
    val encodedRedirect = URLEncoder.encode(config.redirectUri, StandardCharsets.UTF_8.toString)
    s"https://accounts.google.com/o/oauth2/v2/auth?client_id=${config.clientId}&redirect_uri=$encodedRedirect&response_type=code&scope=openid+email+profile&state=$state"

  def createSessionToken(userId: UUID): String =
    val payload   = s"${userId.toString}:${Instant.now().getEpochSecond}"
    val signature = sign(payload)
    val raw       = s"$payload:$signature"
    Base64.getUrlEncoder.encodeToString(raw.getBytes(StandardCharsets.UTF_8))

  def verifySessionToken(token: String): Option[UUID] =
    Try {
      val decoded = new String(Base64.getUrlDecoder.decode(token), StandardCharsets.UTF_8)
      val parts   = decoded.split(":")
      if parts.length == 3 then
        val userIdStr   = parts(0)
        val timestamp   = parts(1)
        val signature   = parts(2)
        val payload     = s"$userIdStr:$timestamp"
        val expectedSig = sign(payload)
        if MessageDigest.isEqual(
            signature.getBytes(StandardCharsets.UTF_8),
            expectedSig.getBytes(StandardCharsets.UTF_8)
          )
        then Some(UUID.fromString(userIdStr))
        else None
      else None
    }.toOption.flatten

  def handleGoogleUser(googleId: String, email: String, name: String, pictureUrl: Option[String]): UserSummary =
    val existing = userRepo.findByGoogleId(googleId)
    val user = existing match
      case Some(u) =>
        val updated = u.copy(email = email, name = name, pictureUrl = pictureUrl)
        userRepo.upsert(updated)
        updated
      case None =>
        val newUser = User(
          id = UUID.randomUUID(),
          googleId = googleId,
          email = email,
          name = name,
          pictureUrl = pictureUrl,
          createdAt = Instant.now()
        )
        userRepo.upsert(newUser)
        newUser

    UserSummary(user.id, user.email, user.name, user.pictureUrl)

  private def sign(data: String): String =
    val mac       = Mac.getInstance("HmacSHA256")
    val secretKey = new SecretKeySpec(config.sessionSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")
    mac.init(secretKey)
    val hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
    Base64.getUrlEncoder.withoutPadding().encodeToString(hmacBytes)
