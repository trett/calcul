package ru.trett.calcul.db

import com.augustnagro.magnum.*
import com.augustnagro.magnum.DbCodec.given
import ru.trett.calcul.db.DbCodecs.given
import ru.trett.calcul.model.*

import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class UserRepository(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  def upsert(user: User): Unit =
    transactor.withConnection {
      sql"""INSERT INTO users (id, google_id, email, name, picture_url, encrypted_gemini_api_key, created_at)
        VALUES (${user.id}, ${user.googleId}, ${user.email}, ${user.name}, ${user.pictureUrl}, ${user.encryptedGeminiApiKey}, ${user.createdAt})
        ON CONFLICT (google_id)
        DO UPDATE SET email = EXCLUDED.email, name = EXCLUDED.name, picture_url = EXCLUDED.picture_url
      """.update.run()
    }

  def findById(id: UUID): Option[User] =
    transactor.withConnection {
      sql"SELECT id, google_id, email, name, picture_url, created_at, encrypted_gemini_api_key FROM users WHERE id = $id"
        .query[User]
        .run()
        .headOption
    }

  def findByGoogleId(googleId: String): Option[User] =
    transactor.withConnection {
      sql"SELECT id, google_id, email, name, picture_url, created_at, encrypted_gemini_api_key FROM users WHERE google_id = $googleId"
        .query[User]
        .run()
        .headOption
    }

  def updateGeminiKey(userId: UUID, encryptedKey: String): Unit =
    transactor.withConnection {
      sql"UPDATE users SET encrypted_gemini_api_key = $encryptedKey WHERE id = $userId".update.run()
    }

  def clearGeminiKey(userId: UUID): Unit =
    transactor.withConnection {
      sql"UPDATE users SET encrypted_gemini_api_key = NULL WHERE id = $userId".update.run()
    }

  def getEncryptedGeminiKey(userId: UUID): Option[String] =
    transactor.withConnection {
      sql"SELECT encrypted_gemini_api_key FROM users WHERE id = $userId"
        .query[Option[String]]
        .run()
        .headOption
        .flatten
    }

class DailyTargetRepository(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  def setTarget(userId: UUID, targetDate: LocalDate, calorieTarget: Int): Unit =
    transactor.withConnection {
      sql"""INSERT INTO daily_targets (user_id, target_date, calorie_target)
        VALUES ($userId, $targetDate, $calorieTarget)
        ON CONFLICT (user_id, target_date)
        DO UPDATE SET calorie_target = EXCLUDED.calorie_target
      """.update.run()
    }

  def findTarget(userId: UUID, targetDate: LocalDate): Option[DailyTarget] =
    transactor.withConnection {
      sql"SELECT user_id, target_date, calorie_target FROM daily_targets WHERE user_id = $userId AND target_date = $targetDate"
        .query[DailyTarget]
        .run()
        .headOption
    }

class MealRepository(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  def insertMeal(meal: Meal): Either[String, Unit] =
    transactor.withTransaction {
      sql"""INSERT INTO meals (id, user_id, logged_at, meal_date, description, image_path, total_calories, ai_explanation)
        VALUES (${meal.id}, ${meal.userId}, ${meal.loggedAt}, ${meal.mealDate}, ${meal.description}, ${meal.imagePath}, ${meal.totalCalories}, ${meal.aiExplanation})
      """.update.run()

      if meal.items.nonEmpty then
        batchUpdate(meal.items): item =>
          sql"INSERT INTO meal_items (id, meal_id, item_name, estimated_calories) VALUES (${item.id}, ${meal.id}, ${item.itemName}, ${item.estimatedCalories})".update
    }

  def findMealsByDate(userId: UUID, mealDate: LocalDate): List[Meal] =
    transactor.withConnection {
      val rawMeals =
        sql"""SELECT id, user_id, logged_at, meal_date, description, image_path, total_calories, ai_explanation
        FROM meals
        WHERE user_id = $userId AND meal_date = $mealDate
        ORDER BY logged_at ASC
      """.query[MealRecord].run()

      if rawMeals.isEmpty then Nil
      else
        val mealIds       = rawMeals.map(_.id).toList
        val itemsByMealId = findItemsForMealIds(mealIds)
        rawMeals.map { m =>
          Meal(
            id = m.id,
            userId = m.userId,
            loggedAt = m.loggedAt,
            mealDate = m.mealDate,
            description = m.description,
            imagePath = m.imagePath,
            totalCalories = m.totalCalories,
            aiExplanation = m.aiExplanation,
            items = itemsByMealId.getOrElse(m.id, Nil)
          )
        }.toList
    }

  def deleteMeal(userId: UUID, mealId: UUID): Boolean =
    transactor.withConnection {
      sql"DELETE FROM meals WHERE id = $mealId AND user_id = $userId".update.run() > 0
    }

  def getDailyCalorieStats(userId: UUID, mealDate: LocalDate): (Int, Int) =
    transactor.withConnection {
      sql"""SELECT COALESCE(SUM(total_calories), 0)::int, COUNT(*)::int
        FROM meals
        WHERE user_id = $userId AND meal_date = $mealDate
      """.query[(Int, Int)].run().headOption.getOrElse((0, 0))
    }

  private def findItemsForMealIds(mealIds: List[UUID])(using DbCon): Map[UUID, List[MealItem]] =
    if mealIds.isEmpty then Map.empty
    else
      val placeholders = mealIds.map(_ => "?").mkString(",")
      val sqlString =
        s"SELECT id, meal_id, item_name, estimated_calories FROM meal_items WHERE meal_id IN ($placeholders)"
      val items = Frag(
        sqlString,
        mealIds,
        (ps, pos) =>
          mealIds.zipWithIndex.foreach { (id, idx) =>
            ps.setObject(pos + idx, id)
          }
          pos + mealIds.size
      ).query[MealItem].run()
      items.toList.groupBy(_.mealId)

class DailyWeightRepository(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  def recordWeight(weight: DailyWeight): Unit =
    transactor.withConnection {
      sql"""INSERT INTO daily_weights (user_id, weigh_date, weight, unit)
        VALUES (${weight.userId}, ${weight.weighDate}, ${weight.weight}, ${weight.unit})
        ON CONFLICT (user_id, weigh_date)
        DO UPDATE SET weight = EXCLUDED.weight, unit = EXCLUDED.unit
      """.update.run()
    }

  def findWeightsInRange(userId: UUID, fromDate: LocalDate, toDate: LocalDate): List[DailyWeight] =
    transactor.withConnection {
      sql"""SELECT user_id, weigh_date, weight, unit
        FROM daily_weights
        WHERE user_id = $userId AND weigh_date >= $fromDate AND weigh_date <= $toDate
        ORDER BY weigh_date ASC
      """.query[DailyWeight].run().toList
    }
