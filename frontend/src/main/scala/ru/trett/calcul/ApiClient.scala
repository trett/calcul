package ru.trett.calcul

import org.scalajs.dom
import org.scalajs.dom.{Headers, HttpMethod, RequestInit, Response}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default.*
import java.time.LocalDate
import java.util.UUID
import ru.trett.calcul.model.*

object ApiClient:

  private def request(
      path: String,
      httpMethod: HttpMethod,
      httpBody: Option[String] = None,
      httpContentType: Option[String] = Some("application/json")
  ): Future[Response] =
    val reqHeaders = new Headers()
    httpContentType.foreach(ct => reqHeaders.set("Content-Type", ct))

    val init = new RequestInit:
      this.method = httpMethod
      this.headers = reqHeaders
      this.credentials = dom.RequestCredentials.include
      httpBody.foreach(b => this.body = b)

    dom.Fetch.fetch(path, init).toFuture

  private def extractErrorMessage(res: Response, defaultPrefix: String): Future[String] =
    res
      .text()
      .toFuture
      .map { body =>
        val clean = body.trim
        if clean.nonEmpty then clean
        else if Option(res.statusText).exists(_.trim.nonEmpty) then s"$defaultPrefix: ${res.statusText}"
        else s"$defaultPrefix (status ${res.status})"
      }
      .recover { _ =>
        s"$defaultPrefix (status ${res.status})"
      }

  private def getJson[T: Reader](path: String): Future[T] =
    request(path, HttpMethod.GET, httpContentType = None).flatMap: res =>
      if res.ok then res.text().toFuture.map(read[T](_))
      else extractErrorMessage(res, s"GET $path failed").flatMap(msg => Future.failed(new RuntimeException(msg)))

  private def postJson[Req: Writer, Res: Reader](path: String, payload: Req): Future[Res] =
    val json = write(payload)
    request(path, HttpMethod.POST, httpBody = Some(json)).flatMap: res =>
      if res.ok then res.text().toFuture.map(read[Res](_))
      else extractErrorMessage(res, s"POST $path failed").flatMap(msg => Future.failed(new RuntimeException(msg)))

  private def putJson[Req: Writer, Res: Reader](path: String, payload: Req): Future[Res] =
    val json = write(payload)
    request(path, HttpMethod.PUT, httpBody = Some(json)).flatMap: res =>
      if res.ok then res.text().toFuture.map(read[Res](_))
      else extractErrorMessage(res, s"PUT $path failed").flatMap(msg => Future.failed(new RuntimeException(msg)))

  // --- Auth APIs ---
  def getLoginUrl(): Future[String] =
    request("/api/auth/login", HttpMethod.GET, httpContentType = None).flatMap: res =>
      if res.ok then res.text().toFuture
      else Future.failed(new RuntimeException(s"Get login URL failed: ${res.status}"))

  def getCurrentUser(): Future[Option[UserSummary]] =
    request("/api/auth/me", HttpMethod.GET, httpContentType = None).flatMap: res =>
      if res.ok then res.text().toFuture.map(t => Some(read[UserSummary](t)))
      else Future.successful(None)

  def logout(): Future[Unit] =
    request("/api/auth/logout", HttpMethod.POST, httpContentType = None).flatMap: res =>
      if res.ok then Future.successful(())
      else Future.failed(new RuntimeException(s"Logout failed: ${res.status}"))

  // --- Meal & AI APIs ---
  def analyzeMeal(req: AnalyzeMealRequest): Future[MealAnalysisResponse] =
    postJson[AnalyzeMealRequest, MealAnalysisResponse]("/api/meals/analyze", req)

  def analyzeMeal(descriptionOrPrompt: String): Future[MealAnalysisResponse] =
    analyzeMeal(AnalyzeMealRequest(description = Some(descriptionOrPrompt)))

  def createMeal(req: CreateMealRequest): Future[Meal] =
    postJson[CreateMealRequest, Meal]("/api/meals", req)

  def listMeals(date: LocalDate): Future[List[Meal]] =
    getJson[List[Meal]](s"/api/meals?date=$date")

  def deleteMeal(mealId: UUID): Future[Unit] =
    request(s"/api/meals/$mealId", HttpMethod.DELETE, httpContentType = None).flatMap: res =>
      if res.ok then Future.successful(())
      else extractErrorMessage(res, "Delete meal failed").flatMap(msg => Future.failed(new RuntimeException(msg)))

  // --- Calorie Goals & Daily Aggregations ---
  def getDailyCalories(date: LocalDate): Future[DailyCalorieSummary] =
    getJson[DailyCalorieSummary](s"/api/calories/daily?date=$date")

  def setDailyTarget(req: SetTargetRequest): Future[DailyTarget] =
    putJson[SetTargetRequest, DailyTarget]("/api/calories/target", req)

  // --- Weight Tracking APIs ---
  def recordWeight(req: RecordWeightRequest): Future[DailyWeight] =
    postJson[RecordWeightRequest, DailyWeight]("/api/weights", req)

  def getWeights(from: LocalDate, to: LocalDate): Future[List[DailyWeight]] =
    getJson[List[DailyWeight]](s"/api/weights?from=$from&to=$to")

  // --- User Settings APIs ---
  def getGeminiKeyStatus(): Future[GeminiKeyStatus] =
    getJson[GeminiKeyStatus]("/api/user/settings/gemini-key")

  def saveGeminiKey(req: SaveGeminiKeyRequest): Future[GeminiKeyStatus] =
    postJson[SaveGeminiKeyRequest, GeminiKeyStatus]("/api/user/settings/gemini-key", req)

  def deleteGeminiKey(): Future[String] =
    request("/api/user/settings/gemini-key", HttpMethod.DELETE, httpContentType = None).flatMap: res =>
      if res.ok then res.text().toFuture
      else extractErrorMessage(res, "Delete Gemini key failed").flatMap(msg => Future.failed(new RuntimeException(msg)))
