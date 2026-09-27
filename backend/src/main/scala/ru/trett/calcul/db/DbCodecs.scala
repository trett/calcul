package ru.trett.calcul.db

import com.augustnagro.magnum.*
import com.augustnagro.magnum.DbCodec.given
import ru.trett.calcul.model.*

import java.sql.Date as SqlDate
import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID

@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
@SqlName("meals")
final case class MealRecord(
    @Id id: UUID,
    userId: UUID,
    loggedAt: Instant,
    mealDate: LocalDate,
    description: String,
    imagePath: Option[String],
    totalCalories: Int,
    aiExplanation: String
)

object DbCodecs:
  given DbCodec[Instant]   = DbCodec.OffsetDateTimeCodec.biMap(_.toInstant, _.atOffset(ZoneOffset.UTC))
  given DbCodec[LocalDate] = DbCodec.SqlDateCodec.biMap(_.toLocalDate, SqlDate.valueOf)

  given DbCodec[MealRecord]  = DbCodec.derived[MealRecord]
  given DbCodec[User]        = DbCodec.derived[User]
  given DbCodec[DailyTarget] = DbCodec.derived[DailyTarget]
  given DbCodec[MealItem]    = DbCodec.derived[MealItem]
  given DbCodec[DailyWeight] = DbCodec.derived[DailyWeight]
