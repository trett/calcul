package ru.trett.calcul

import java.time.{Instant, LocalDate}
import scala.scalajs.js

object DateUtils:

  def today(): LocalDate =
    val d = new js.Date()
    LocalDate.of(d.getFullYear().toInt, d.getMonth().toInt + 1, d.getDate().toInt)

  def formatTime(instant: Instant): String =
    val d = new js.Date(instant.toEpochMilli.toDouble)
    val h = d.getHours().toInt
    val m = d.getMinutes().toInt
    f"$h%02d:$m%02d"

  private val Weekdays = Vector("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
  private val Months   = Vector("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  def formatFriendlyDate(date: LocalDate): String =
    val weekday = Weekdays((date.getDayOfWeek.getValue - 1 + Weekdays.size) % Weekdays.size)
    s"$weekday, ${Months(date.getMonthValue - 1)} ${date.getDayOfMonth}"
