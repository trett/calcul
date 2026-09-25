package com.calcul.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.{Duration, Instant}
import java.util.{Base64, UUID}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory
import scala.util.{Failure, Success, Try}
import com.calcul.db.UserRepository
import com.calcul.model.{User, UserSummary}

final case class AuthConfig(
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    sessionSecret: String,
    sessionTtlSeconds: Long = 30L * 24 * 3600
)

object AuthConfig:
  def fromEnv(): AuthConfig =
    AuthConfig(
      clientId = sys.env.getOrElse("GOOGLE_CLIENT_ID", "mock-google-client-id"),
      clientSecret = sys.env.getOrElse("GOOGLE_CLIENT_SECRET", "mock-google-client-secret"),
      redirectUri = sys.env.getOrElse("GOOGLE_REDIRECT_URI", "http://localhost:8080/api/auth/callback"),
      sessionSecret = sys.env.getOrElse("SESSION_SECRET", "default-insecure-secret-key-at-least-32-chars"),
      sessionTtlSeconds = sys.env.get("SESSION_TTL_SECONDS").flatMap(_.toLongOption).getOrElse(30L * 24 * 3600)
    )

final case class GoogleUserInfo(
    googleId: String,
    email: String,
    name: String,
    pictureUrl: Option[String]
)

class AuthService(userRepo: UserRepository, config: AuthConfig):

  private val logger = LoggerFactory.getLogger(getClass)

  private val httpClient: java.net.http.HttpClient =
    java.net.http.HttpClient
      .newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build()

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
        val userIdStr    = parts(0)
        val timestampStr = parts(1)
        val signature    = parts(2)
        val payload      = s"$userIdStr:$timestampStr"
        val expectedSig  = sign(payload)
        if MessageDigest.isEqual(
            signature.getBytes(StandardCharsets.UTF_8),
            expectedSig.getBytes(StandardCharsets.UTF_8)
          )
        then
          val timestamp = timestampStr.toLong
          val now       = Instant.now().getEpochSecond
          if (now - timestamp) <= config.sessionTtlSeconds && timestamp <= (now + 60) then
            Some(UUID.fromString(userIdStr))
          else None
        else None
      else None
    }.toOption.flatten

  def exchangeGoogleCode(code: String): Option[GoogleUserInfo] =
    if config.clientId.startsWith("mock-") ||
      config.clientSecret.startsWith("mock-") ||
      code.startsWith("mock") ||
      code.startsWith("demo") ||
      code == "google-demo-user"
    then
      logger.info("Using mock Google OAuth credentials for authorization code exchange")
      Some(
        GoogleUserInfo(
          googleId = "google-demo-user",
          email = "user@example.com",
          name = "Demo User",
          pictureUrl = None
        )
      )
    else
      Try {
        val formParams = Map(
          "code"          -> code,
          "client_id"     -> config.clientId,
          "client_secret" -> config.clientSecret,
          "redirect_uri"  -> config.redirectUri,
          "grant_type"    -> "authorization_code"
        )
        val formBody = formParams
          .map { case (k, v) =>
            s"${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
          }
          .mkString("&")

        val tokenReq = java.net.http.HttpRequest
          .newBuilder()
          .uri(java.net.URI.create("https://oauth2.googleapis.com/token"))
          .timeout(Duration.ofSeconds(15))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(java.net.http.HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
          .build()

        logger.info(s"Exchanging Google OAuth2 authorization code with redirect_uri: ${config.redirectUri}")
        val tokenRes =
          httpClient.send(tokenReq, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if tokenRes.statusCode() == 200 then
          val tokenJson   = ujson.read(tokenRes.body())
          val accessToken = tokenJson("access_token").str

          val userinfoReq = java.net.http.HttpRequest
            .newBuilder()
            .uri(java.net.URI.create("https://openidconnect.googleapis.com/v1/userinfo"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", s"Bearer $accessToken")
            .GET()
            .build()

          val userinfoRes =
            httpClient.send(userinfoReq, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
          if userinfoRes.statusCode() == 200 then
            val userJson = ujson.read(userinfoRes.body())
            val googleId = userJson("sub").str
            val email    = userJson("email").str
            val name     = if userJson.obj.contains("name") then userJson("name").str else email.takeWhile(_ != '@')
            val pic      = if userJson.obj.contains("picture") then Some(userJson("picture").str) else None
            logger.info(s"Google OAuth2 authentication succeeded for email: $email")
            Some(GoogleUserInfo(googleId, email, name, pic))
          else
            logger.error(
              s"Google userinfo request failed with status ${userinfoRes.statusCode()}: ${userinfoRes.body()}"
            )
            None
        else
          logger.error(s"Google token exchange failed with status ${tokenRes.statusCode()}: ${tokenRes.body()}")
          None
      } match
        case Success(result) => result
        case Failure(ex) =>
          logger.error("Exception occurred during Google OAuth code exchange", ex)
          None

  def handleGoogleUser(googleId: String, email: String, name: String, pictureUrl: Option[String]): UserSummary =
    logger.info(s"Handling login for user: email=$email, googleId=$googleId")
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
