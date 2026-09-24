package com.calcul

import com.raquo.airstream.state.Var
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalDate
import com.calcul.model.*

object AppState:

  val currentUser: Var[Option[UserSummary]]          = Var(None)
  val selectedDate: Var[LocalDate]                   = Var(DateUtils.today())
  val theme: Var[String]                             = Var(loadSavedTheme())
  val activeTab: Var[String]                         = Var("dashboard")
  val notification: Var[Option[(String, String)]]    = Var(None)
  val dailySummary: Var[Option[DailyCalorieSummary]] = Var(None)
  val meals: Var[List[Meal]]                         = Var(Nil)
  val weights: Var[List[DailyWeight]]                = Var(Nil)
  val isLoading: Var[Boolean]                        = Var(false)
  val isAuthChecking: Var[Boolean]                   = Var(true)
  val isSettingsOpen: Var[Boolean]                   = Var(false)

  private def loadSavedTheme(): String =
    Option(dom.window.localStorage.getItem("caltrack_theme")).filter(_.nonEmpty).getOrElse("light")

  def initTheme(): Unit =
    val current = theme.now()
    applyThemeClass(current)

  def toggleTheme(): Unit =
    val newTheme = if theme.now() == "dark" then "light" else "dark"
    theme.set(newTheme)
    dom.window.localStorage.setItem("caltrack_theme", newTheme)
    applyThemeClass(newTheme)

  private def applyThemeClass(t: String): Unit =
    val cl = dom.document.documentElement.classList
    if t == "dark" then
      cl.add("sl-theme-dark")
      cl.remove("sl-theme-light")
    else
      cl.remove("sl-theme-dark")
      cl.add("sl-theme-light")

  def notify(message: String, variant: String = "primary"): Unit =
    notification.set(Some((variant, message)))
    dom.window.setTimeout(() => notification.set(None), 4000)

  def setDate(d: LocalDate): Unit =
    selectedDate.set(d)
    loadDailyData()

  def setPreviousDay(): Unit =
    setDate(selectedDate.now().minusDays(1))

  def setNextDay(): Unit =
    setDate(selectedDate.now().plusDays(1))

  def setToday(): Unit =
    setDate(DateUtils.today())

  def loadCurrentUser(): Unit =
    isAuthChecking.set(true)
    ApiClient
      .getCurrentUser()
      .foreach: userOpt =>
        currentUser.set(userOpt)
        isAuthChecking.set(false)
        if userOpt.isDefined then loadDailyData()

  def loadDailyData(): Unit =
    val d = selectedDate.now()
    isLoading.set(true)
    ApiClient
      .getDailyCalories(d)
      .foreach: summary =>
        dailySummary.set(Some(summary))
        isLoading.set(false)
    ApiClient
      .listMeals(d)
      .foreach: mealList =>
        meals.set(mealList)

  def loadWeights(): Unit =
    val to   = selectedDate.now()
    val from = to.minusDays(30)
    ApiClient
      .getWeights(from, to)
      .foreach: weightList =>
        weights.set(weightList)
