package com.calcul.db

import munit.FunSuite
import java.sql.{Connection, DriverManager}
import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.compiletime.uninitialized
import com.calcul.model.*

class RepositorySuite extends FunSuite:

  var conn: Connection                  = uninitialized
  var userRepo: UserRepository          = uninitialized
  var targetRepo: DailyTargetRepository = uninitialized
  var mealRepo: MealRepository          = uninitialized
  var weightRepo: DailyWeightRepository = uninitialized

  override def beforeEach(context: BeforeEach): Unit =
    val jdbcUrl = s"jdbc:h2:mem:repo_test_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    conn = DriverManager.getConnection(jdbcUrl, "sa", "")
    TestDbInit.initSchema(conn)
    userRepo = new UserRepository(conn)
    targetRepo = new DailyTargetRepository(conn)
    mealRepo = new MealRepository(conn)
    weightRepo = new DailyWeightRepository(conn)

  override def afterEach(context: AfterEach): Unit =
    if conn != null && !conn.isClosed then conn.close()

  test("UserRepository upserts and finds users by ID and Google ID") {
    val userId = UUID.randomUUID()
    val user = User(
      id = userId,
      googleId = "g-1001",
      email = "alice@example.com",
      name = "Alice",
      pictureUrl = Some("https://example.com/alice.jpg"),
      createdAt = Instant.now()
    )

    userRepo.upsert(user)
    val foundById = userRepo.findById(userId)
    assertEquals(foundById.map(_.email), Some("alice@example.com"))

    val foundByGoogle = userRepo.findByGoogleId("g-1001")
    assertEquals(foundByGoogle.map(_.name), Some("Alice"))

    val updated = user.copy(name = "Alice In Chains")
    userRepo.upsert(updated)
    assertEquals(userRepo.findById(userId).map(_.name), Some("Alice In Chains"))
  }

  test("DailyTargetRepository sets and retrieves daily targets") {
    val userId = UUID.randomUUID()
    val user   = User(userId, "g-2002", "bob@example.com", "Bob", None, Instant.now())
    userRepo.upsert(user)

    val today = LocalDate.parse("2026-09-20")
    targetRepo.setTarget(userId, today, 2100)
    val found = targetRepo.findTarget(userId, today)
    assertEquals(found.map(_.calorieTarget), Some(2100))

    targetRepo.setTarget(userId, today, 1950)
    assertEquals(targetRepo.findTarget(userId, today).map(_.calorieTarget), Some(1950))
  }

  test("MealRepository inserts, lists and deletes meals with items") {
    val userId = UUID.randomUUID()
    val user   = User(userId, "g-3003", "carol@example.com", "Carol", None, Instant.now())
    userRepo.upsert(user)

    val mealId = UUID.randomUUID()
    val today  = LocalDate.parse("2026-09-20")
    val item1  = MealItem(UUID.randomUUID(), mealId, "Salmon 200g", 400)
    val item2  = MealItem(UUID.randomUUID(), mealId, "Quinoa 150g", 220)
    val meal = Meal(
      id = mealId,
      userId = userId,
      loggedAt = Instant.now(),
      mealDate = today,
      description = "Salmon with Quinoa",
      imagePath = None,
      totalCalories = 620,
      aiExplanation = "Nutritious dinner with omega-3",
      items = List(item1, item2)
    )

    mealRepo.insertMeal(meal)
    val meals = mealRepo.findMealsByDate(userId, today)
    assertEquals(meals.size, 1)
    assertEquals(meals.head.items.size, 2)
    assertEquals(meals.head.totalCalories, 620)

    val deleted = mealRepo.deleteMeal(userId, mealId)
    assert(deleted)
    assertEquals(mealRepo.findMealsByDate(userId, today).size, 0)
  }

  test("DailyWeightRepository records and queries weights in date range") {
    val userId = UUID.randomUUID()
    val user   = User(userId, "g-4004", "dan@example.com", "Dan", None, Instant.now())
    userRepo.upsert(user)

    val d1 = LocalDate.parse("2026-09-18")
    val d2 = LocalDate.parse("2026-09-19")
    val d3 = LocalDate.parse("2026-09-20")

    weightRepo.recordWeight(DailyWeight(userId, d1, BigDecimal("80.20"), "kg"))
    weightRepo.recordWeight(DailyWeight(userId, d2, BigDecimal("79.90"), "kg"))
    weightRepo.recordWeight(DailyWeight(userId, d3, BigDecimal("79.50"), "kg"))

    val range = weightRepo.findWeightsInRange(userId, d1, d2)
    assertEquals(range.size, 2)
    assertEquals(range.map(_.weight), List(BigDecimal("80.20"), BigDecimal("79.90")))
  }
