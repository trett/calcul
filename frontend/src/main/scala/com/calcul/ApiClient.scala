package com.calcul

import org.scalajs.dom
import org.scalajs.dom.{Headers, HttpMethod, RequestInit, Response}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default.*
import java.time.LocalDate
import java.util.UUID
import com.calcul.model.*

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

  private def getJson[T: Reader](path: String): Future[T] =
    request(path, HttpMethod.GET, httpContentType = None).flatMap: res =>
      if res.ok then res.text().toFuture.map(read[T](_))
      else Future.failed(new RuntimeException(s"GET $path failed with status ${res.status}: ${res.statusText}"))

  private def postJson[Req: Writer, Res: Reader](path: String, payload: Req): Future[Res] =
    val json = write(payload)
    request(path, HttpMethod.POST, httpBody = Some(json)).flatMap: res =>
      if res.ok then res.text().toFuture.map(read[Res](_))
      else Future.failed(new RuntimeException(s"POST $path failed with status ${res.status}: ${res.statusText}"))

  private def putJson[Req: Writer, Res: Reader](path: String, payload: Req): Future[Res] =
    val json = write(payload)
    request(path, HttpMethod.PUT, httpBody = Some(json)).flatMap: res =>
      if res.ok then res.text().toFuture.map(read[Res](_))
      else Future.failed(new RuntimeException(s"PUT $path failed with status ${res.status}: ${res.statusText}"))

  // --- Auth APIs ---
  def getCurrentUser(): Future[Option[UserSummary]] =
    request("/api/auth/me", HttpMethod.GET, httpContentType = None).flatMap: res =>
      if res.ok then res.text().toFuture.map(t => Some(read[UserSummary](t)))
      else Future.successful(None)

  def logout(): Future[Unit] =
    request("/api/auth/logout", HttpMethod.POST, httpContentType = None).flatMap: res =>
      if res.ok then Future.successful(())
      else Future.failed(new RuntimeException(s"Logout failed: ${res.status}"))

  // --- Meal & AI APIs ---
  def analyzeMeal(descriptionOrPrompt: String): Future[MealAnalysisResponse] =
    request(
      "/api/meals/analyze",
      HttpMethod.POST,
      httpBody = Some(descriptionOrPrompt),
      httpContentType = Some("text/plain")
    ).flatMap: res =>
      if res.ok then res.text().toFuture.map(read[MealAnalysisResponse](_))
      else Future.failed(new RuntimeException(s"Analyze failed with status ${res.status}"))

  def createMeal(req: CreateMealRequest): Future[Meal] =
    postJson[CreateMealRequest, Meal]("/api/meals", req)

  def listMeals(date: LocalDate): Future[List[Meal]] =
    getJson[List[Meal]](s"/api/meals?date=$date")

  def deleteMeal(mealId: UUID): Future[Unit] =
    request(s"/api/meals/$mealId", HttpMethod.DELETE, httpContentType = None).flatMap: res =>
      if res.ok then Future.successful(())
      else Future.failed(new RuntimeException(s"Delete meal failed: ${res.status}"))

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
