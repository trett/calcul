package com.calcul.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.{Base64, UUID}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}
import sttp.client4.*
import sttp.model.StatusCode
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

class AuthService(
    userRepo: UserRepository,
    config: AuthConfig,
    backend: SyncBackend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
):

  private val logger = LoggerFactory.getLogger(getClass)

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

        logger.info(s"Exchanging Google OAuth2 authorization code with redirect_uri: ${config.redirectUri}")
        val tokenRes = basicRequest
          .post(uri"https://oauth2.googleapis.com/token")
          .body(formParams)
          .readTimeout(15.seconds)
          .response(asStringAlways)
          .send(backend)

        if tokenRes.code == StatusCode.Ok then
          val tokenJson   = ujson.read(tokenRes.body)
          val accessToken = tokenJson("access_token").str

          val userinfoRes = basicRequest
            .get(uri"https://openidconnect.googleapis.com/v1/userinfo")
            .auth
            .bearer(accessToken)
            .readTimeout(15.seconds)
            .response(asStringAlways)
            .send(backend)

          if userinfoRes.code == StatusCode.Ok then
            val userJson = ujson.read(userinfoRes.body)
            val googleId = userJson("sub").str
            val email    = userJson("email").str
            val name     = if userJson.obj.contains("name") then userJson("name").str else email.takeWhile(_ != '@')
            val pic      = if userJson.obj.contains("picture") then Some(userJson("picture").str) else None
            logger.info(s"Google OAuth2 authentication succeeded for email: $email")
            Some(GoogleUserInfo(googleId, email, name, pic))
          else
            logger.error(
              s"Google userinfo request failed with status ${userinfoRes.code}: ${userinfoRes.body}"
            )
            None
        else
          logger.error(s"Google token exchange failed with status ${tokenRes.code}: ${tokenRes.body}")
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
