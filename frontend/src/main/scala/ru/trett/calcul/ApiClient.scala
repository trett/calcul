package ru.trett.calcul

import org.scalajs.dom
import ru.trett.calcul.api.Endpoints
import ru.trett.calcul.model.*
import sttp.client4.fetch.{FetchBackend, FetchOptions}
import sttp.model.StatusCode
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.{DecodeResult, PublicEndpoint}

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ApiClient:

  private val backend =
    FetchBackend(FetchOptions(credentials = Some(dom.RequestCredentials.include), mode = None))
  private val interpreter = SttpClientInterpreter()

  private def send[I, E, O](
      endpoint: PublicEndpoint[I, E, O, Any],
      input: I
  ): Future[O] =
    val req = interpreter.toRequest(endpoint, None).apply(input)
    req.send(backend).flatMap { res =>
      res.body match
        case DecodeResult.Value(Right(out)) =>
          Future.successful(out)
        case DecodeResult.Value(Left((code: StatusCode, msg: String))) =>
          val text = if msg.trim.nonEmpty then msg.trim else s"Request failed ($code)"
          Future.failed(new RuntimeException(text))
        case DecodeResult.Value(Left(msg: String)) =>
          val text = if msg.trim.nonEmpty then msg.trim else s"Request failed (${res.code})"
          Future.failed(new RuntimeException(text))
        case DecodeResult.Value(Left(())) =>
          Future.failed(new RuntimeException(s"Request failed (${res.code})"))
        case DecodeResult.Value(Left(other)) =>
          Future.failed(new RuntimeException(s"Request failed: $other"))
        case DecodeResult.Error(_, error) =>
          Future.failed(new RuntimeException(s"Failed to decode response: ${error.getMessage}"))
        case failure =>
          Future.failed(new RuntimeException(s"Failed to decode response: $failure"))
    }

  // --- Auth APIs ---
  def getLoginUrl(): Future[String] =
    send(Endpoints.loginEndpoint, ())

  def getCurrentUser(): Future[Option[UserSummary]] =
    val req = interpreter.toRequest(Endpoints.meEndpoint, None).apply(None)
    req.send(backend).map { res =>
      res.body match
        case DecodeResult.Value(Right(summary)) => Some(summary)
        case _                                  => None
    }

  def logout(): Future[Unit] =
    send(Endpoints.logoutEndpoint, ()).map(_ => ())

  // --- Meal & AI APIs ---
  def analyzeMeal(req: AnalyzeMealRequest): Future[MealAnalysisResponse] =
    send(Endpoints.analyzeMealEndpoint, (None, req))

  def analyzeMeal(descriptionOrPrompt: String): Future[MealAnalysisResponse] =
    analyzeMeal(AnalyzeMealRequest(description = Some(descriptionOrPrompt)))

  def createMeal(req: CreateMealRequest): Future[Meal] =
    send(Endpoints.createMealEndpoint, (None, req))

  def listMeals(date: LocalDate): Future[List[Meal]] =
    send(Endpoints.listMealsEndpoint, (None, date.toString))

  def deleteMeal(mealId: UUID): Future[Unit] =
    send(Endpoints.deleteMealEndpoint, (None, mealId.toString)).map(_ => ())

  // --- Calorie Goals & Daily Aggregations ---
  def getDailyCalories(date: LocalDate): Future[DailyCalorieSummary] =
    send(Endpoints.getDailyCaloriesEndpoint, (None, date.toString))

  def setDailyTarget(req: SetTargetRequest): Future[DailyTarget] =
    send(Endpoints.setDailyTargetEndpoint, (None, req))

  // --- Weight Tracking APIs ---
  def recordWeight(req: RecordWeightRequest): Future[DailyWeight] =
    send(Endpoints.recordWeightEndpoint, (None, req))

  def getWeights(from: LocalDate, to: LocalDate): Future[List[DailyWeight]] =
    send(Endpoints.getWeightsEndpoint, (None, from.toString, to.toString))

  // --- User Settings APIs ---
  def getGeminiKeyStatus(): Future[GeminiKeyStatus] =
    send(Endpoints.getGeminiKeyStatusEndpoint, None)

  def saveGeminiKey(req: SaveGeminiKeyRequest): Future[GeminiKeyStatus] =
    send(Endpoints.saveGeminiKeyEndpoint, (None, req))

  def deleteGeminiKey(): Future[String] =
    send(Endpoints.deleteGeminiKeyEndpoint, None)
