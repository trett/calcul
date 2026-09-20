package com.calcul.model

import upickle.default.*
import java.time.{Instant, LocalDate}
import java.util.UUID
import com.calcul.model.Codecs.given

final case class User(
    id: UUID,
    googleId: String,
    email: String,
    name: String,
    pictureUrl: Option[String],
    createdAt: Instant
) derives ReadWriter

final case class UserSummary(
    id: UUID,
    email: String,
    name: String,
    pictureUrl: Option[String]
) derives ReadWriter

final case class DailyTarget(
    userId: UUID,
    targetDate: LocalDate,
    calorieTarget: Int
) derives ReadWriter

final case class SetTargetRequest(
    targetDate: LocalDate,
    calorieTarget: Int
) derives ReadWriter

final case class DailyCalorieSummary(
    targetDate: LocalDate,
    calorieTarget: Int,
    totalConsumed: Int,
    remainingCalories: Int,
    mealsCount: Int
) derives ReadWriter

final case class MealItem(
    id: UUID,
    mealId: UUID,
    itemName: String,
    estimatedCalories: Int
) derives ReadWriter

final case class Meal(
    id: UUID,
    userId: UUID,
    loggedAt: Instant,
    mealDate: LocalDate,
    description: String,
    imagePath: Option[String],
    totalCalories: Int,
    aiExplanation: String,
    items: List[MealItem]
) derives ReadWriter

final case class CreateMealItem(
    itemName: String,
    estimatedCalories: Int
) derives ReadWriter

final case class CreateMealRequest(
    mealDate: LocalDate,
    description: String,
    totalCalories: Int,
    aiExplanation: String,
    items: List[CreateMealItem]
) derives ReadWriter

final case class AnalyzedItem(
    name: String,
    calories: Int
) derives ReadWriter

final case class MealAnalysisResponse(
    items: List[AnalyzedItem],
    totalCalories: Int,
    explanation: String
) derives ReadWriter

final case class DailyWeight(
    userId: UUID,
    weighDate: LocalDate,
    weight: BigDecimal,
    unit: String
) derives ReadWriter

final case class RecordWeightRequest(
    weighDate: LocalDate,
    weight: BigDecimal,
    unit: String
) derives ReadWriter
