package ru.trett.calcul.db

import java.sql.{Connection, Date as SqlDate, ResultSet, Timestamp}
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import scala.util.{Try, Using}
import ru.trett.calcul.model.*

class UserRepository(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  def upsert(user: User): Unit =
    transactor.withConnection { conn =>
      val sql =
        """INSERT INTO users (id, google_id, email, name, picture_url, encrypted_gemini_api_key, created_at)
          |VALUES (?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (google_id)
          |DO UPDATE SET email = EXCLUDED.email, name = EXCLUDED.name, picture_url = EXCLUDED.picture_url
        """.stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, user.id)
        ps.setString(2, user.googleId)
        ps.setString(3, user.email)
        ps.setString(4, user.name)
        ps.setString(5, user.pictureUrl.orNull)
        ps.setString(6, user.encryptedGeminiApiKey.orNull)
        ps.setTimestamp(7, Timestamp.from(user.createdAt))
        ps.executeUpdate()
      }
    }

  def findById(id: UUID): Option[User] =
    transactor.withConnection { conn =>
      val sql =
        "SELECT id, google_id, email, name, picture_url, encrypted_gemini_api_key, created_at FROM users WHERE id = ?"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, id)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Some(mapUser(rs)) else None
        }
      }
    }

  def findByGoogleId(googleId: String): Option[User] =
    transactor.withConnection { conn =>
      val sql =
        "SELECT id, google_id, email, name, picture_url, encrypted_gemini_api_key, created_at FROM users WHERE google_id = ?"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, googleId)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Some(mapUser(rs)) else None
        }
      }
    }

  def updateGeminiKey(userId: UUID, encryptedKey: String): Unit =
    transactor.withConnection { conn =>
      val sql = "UPDATE users SET encrypted_gemini_api_key = ? WHERE id = ?"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, encryptedKey)
        ps.setObject(2, userId)
        ps.executeUpdate()
      }
    }

  def clearGeminiKey(userId: UUID): Unit =
    transactor.withConnection { conn =>
      val sql = "UPDATE users SET encrypted_gemini_api_key = NULL WHERE id = ?"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId)
        ps.executeUpdate()
      }
    }

  def getEncryptedGeminiKey(userId: UUID): Option[String] =
    transactor.withConnection { conn =>
      val sql = "SELECT encrypted_gemini_api_key FROM users WHERE id = ?"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Option(rs.getString("encrypted_gemini_api_key")) else None
        }
      }
    }

  private def mapUser(rs: ResultSet): User =
    val idObj = rs.getObject("id")
    val id = idObj match
      case u: UUID   => u
      case s: String => UUID.fromString(s)
      case other     => UUID.fromString(other.toString)
    val encKey    = Try(rs.getString("encrypted_gemini_api_key")).toOption.flatMap(Option(_))
    val createdAt = Option(rs.getTimestamp("created_at")).map(_.toInstant).getOrElse(java.time.Instant.now())
    User(
      id = id,
      googleId = rs.getString("google_id"),
      email = rs.getString("email"),
      name = rs.getString("name"),
      pictureUrl = Option(rs.getString("picture_url")),
      createdAt = createdAt,
      encryptedGeminiApiKey = encKey
    )

class DailyTargetRepository(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  def setTarget(userId: UUID, targetDate: LocalDate, calorieTarget: Int): Unit =
    transactor.withConnection { conn =>
      val sql =
        """INSERT INTO daily_targets (user_id, target_date, calorie_target)
          |VALUES (?, ?, ?)
          |ON CONFLICT (user_id, target_date)
          |DO UPDATE SET calorie_target = EXCLUDED.calorie_target
        """.stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId)
        ps.setDate(2, SqlDate.valueOf(targetDate))
        ps.setInt(3, calorieTarget)
        ps.executeUpdate()
      }
    }

  def findTarget(userId: UUID, targetDate: LocalDate): Option[DailyTarget] =
    transactor.withConnection { conn =>
      val sql = "SELECT user_id, target_date, calorie_target FROM daily_targets WHERE user_id = ? AND target_date = ?"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId)
        ps.setDate(2, SqlDate.valueOf(targetDate))
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then
            Some(
              DailyTarget(
                userId = rs.getObject("user_id", classOf[UUID]),
                targetDate = rs.getDate("target_date").toLocalDate,
                calorieTarget = rs.getInt("calorie_target")
              )
            )
          else None
        }
      }
    }

class MealRepository(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  def insertMeal(meal: Meal): Unit =
    transactor.withTransaction { conn =>
      val mealSql =
        """INSERT INTO meals (id, user_id, logged_at, meal_date, description, image_path, total_calories, ai_explanation)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.stripMargin
      Using.resource(conn.prepareStatement(mealSql)) { ps =>
        ps.setObject(1, meal.id)
        ps.setObject(2, meal.userId)
        ps.setTimestamp(3, Timestamp.from(meal.loggedAt))
        ps.setDate(4, SqlDate.valueOf(meal.mealDate))
        ps.setString(5, meal.description)
        ps.setString(6, meal.imagePath.orNull)
        ps.setInt(7, meal.totalCalories)
        ps.setString(8, meal.aiExplanation)
        ps.executeUpdate()
      }

      if meal.items.nonEmpty then
        val itemSql = "INSERT INTO meal_items (id, meal_id, item_name, estimated_calories) VALUES (?, ?, ?, ?)"
        Using.resource(conn.prepareStatement(itemSql)) { ps =>
          for item <- meal.items do
            ps.setObject(1, item.id)
            ps.setObject(2, meal.id)
            ps.setString(3, item.itemName)
            ps.setInt(4, item.estimatedCalories)
            ps.addBatch()
          ps.executeBatch()
        }
    }

  def findMealsByDate(userId: UUID, mealDate: LocalDate): List[Meal] =
    transactor.withConnection { conn =>
      val mealSql =
        """SELECT id, user_id, logged_at, meal_date, description, image_path, total_calories, ai_explanation
          |FROM meals
          |WHERE user_id = ? AND meal_date = ?
          |ORDER BY logged_at ASC
        """.stripMargin

      val mealBuffers = collection.mutable.ListBuffer[Meal]()
      Using.resource(conn.prepareStatement(mealSql)) { ps =>
        ps.setObject(1, userId)
        ps.setDate(2, SqlDate.valueOf(mealDate))
        Using.resource(ps.executeQuery()) { rs =>
          while rs.next() do
            mealBuffers += Meal(
              id = rs.getObject("id", classOf[UUID]),
              userId = rs.getObject("user_id", classOf[UUID]),
              loggedAt = rs.getTimestamp("logged_at").toInstant,
              mealDate = rs.getDate("meal_date").toLocalDate,
              description = rs.getString("description"),
              imagePath = Option(rs.getString("image_path")),
              totalCalories = rs.getInt("total_calories"),
              aiExplanation = rs.getString("ai_explanation"),
              items = Nil
            )
        }
      }

      val rawMeals = mealBuffers.toList
      if rawMeals.isEmpty then Nil
      else
        val mealIds       = rawMeals.map(_.id)
        val itemsByMealId = findItemsForMealIds(conn, mealIds)
        rawMeals.map(m => m.copy(items = itemsByMealId.getOrElse(m.id, Nil)))
    }

  def deleteMeal(userId: UUID, mealId: UUID): Boolean =
    transactor.withConnection { conn =>
      val sql = "DELETE FROM meals WHERE id = ? AND user_id = ?"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, mealId)
        ps.setObject(2, userId)
        ps.executeUpdate() > 0
      }
    }

  def getDailyCalorieStats(userId: UUID, mealDate: LocalDate): (Int, Int) =
    transactor.withConnection { conn =>
      val sql =
        """SELECT COALESCE(SUM(total_calories), 0) AS total_cals, COUNT(*) AS meal_count
          |FROM meals
          |WHERE user_id = ? AND meal_date = ?
        """.stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId)
        ps.setDate(2, SqlDate.valueOf(mealDate))
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then (rs.getInt("total_cals"), rs.getInt("meal_count"))
          else (0, 0)
        }
      }
    }

  private def findItemsForMealIds(conn: Connection, mealIds: List[UUID]): Map[UUID, List[MealItem]] =
    if mealIds.isEmpty then Map.empty
    else
      val placeholders = mealIds.map(_ => "?").mkString(",")
      val itemSql =
        s"SELECT id, meal_id, item_name, estimated_calories FROM meal_items WHERE meal_id IN ($placeholders)"
      val items = collection.mutable.ListBuffer[MealItem]()
      Using.resource(conn.prepareStatement(itemSql)) { ps =>
        mealIds.zipWithIndex.foreach { case (id, idx) =>
          ps.setObject(idx + 1, id)
        }
        Using.resource(ps.executeQuery()) { rs =>
          while rs.next() do
            items += MealItem(
              id = rs.getObject("id", classOf[UUID]),
              mealId = rs.getObject("meal_id", classOf[UUID]),
              itemName = rs.getString("item_name"),
              estimatedCalories = rs.getInt("estimated_calories")
            )
        }
      }
      items.toList.groupBy(_.mealId)

class DailyWeightRepository(transactor: DbTransactor):

  def this(ds: DataSource) = this(DbTransactor.fromDataSource(ds))
  def this(conn: Connection) = this(DbTransactor.fromConnection(conn))

  def recordWeight(weight: DailyWeight): Unit =
    transactor.withConnection { conn =>
      val sql =
        """INSERT INTO daily_weights (user_id, weigh_date, weight, unit)
          |VALUES (?, ?, ?, ?)
          |ON CONFLICT (user_id, weigh_date)
          |DO UPDATE SET weight = EXCLUDED.weight, unit = EXCLUDED.unit
        """.stripMargin
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, weight.userId)
        ps.setDate(2, SqlDate.valueOf(weight.weighDate))
        ps.setBigDecimal(3, weight.weight.bigDecimal)
        ps.setString(4, weight.unit)
        ps.executeUpdate()
      }
    }

  def findWeightsInRange(userId: UUID, fromDate: LocalDate, toDate: LocalDate): List[DailyWeight] =
    transactor.withConnection { conn =>
      val sql =
        """SELECT user_id, weigh_date, weight, unit
          |FROM daily_weights
          |WHERE user_id = ? AND weigh_date >= ? AND weigh_date <= ?
          |ORDER BY weigh_date ASC
        """.stripMargin
      val weights = collection.mutable.ListBuffer[DailyWeight]()
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId)
        ps.setDate(2, SqlDate.valueOf(fromDate))
        ps.setDate(3, SqlDate.valueOf(toDate))
        Using.resource(ps.executeQuery()) { rs =>
          while rs.next() do
            weights += DailyWeight(
              userId = rs.getObject("user_id", classOf[UUID]),
              weighDate = rs.getDate("weigh_date").toLocalDate,
              weight = BigDecimal(rs.getBigDecimal("weight")),
              unit = rs.getString("unit")
            )
        }
      }
      weights.toList
    }
