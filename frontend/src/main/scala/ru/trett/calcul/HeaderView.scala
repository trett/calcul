package ru.trett.calcul

import com.raquo.laminar.api.L.*
import java.time.LocalDate
import ru.trett.calcul.ShoelaceDSL.*

object HeaderView:

  def apply(): HtmlElement =
    headerTag(
      cls := "app-header",
      styleAttr := "display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.5rem; border-bottom: 1px solid var(--sl-color-neutral-200); background-color: var(--sl-panel-background-color); flex-wrap: wrap; gap: 1rem;",

      // Brand / Logo
      div(
        styleAttr := "display: flex; align-items: center; gap: 0.5rem; cursor: pointer;",
        onClick --> (_ => AppState.activeTab.set("dashboard")),
        slIcon(slName := "fire", styleAttr := "font-size: 1.8rem; color: var(--sl-color-primary-600);"),
        span(
          styleAttr := "font-size: 1.35rem; font-weight: 700; letter-spacing: -0.5px; color: var(--sl-color-neutral-900);",
          "CalTrack"
        ),
        span(
          styleAttr := "background-color: var(--sl-color-primary-50); color: var(--sl-color-primary-700); font-size: 0.75rem; font-weight: 700; padding: 0.15rem 0.45rem; border-radius: 9999px; border: 1px solid var(--sl-color-primary-200);",
          "AI"
        )
      ),

      // Authenticated Navigation: Date Navigator & Navigation Tabs
      child.maybe <-- AppState.currentUser.signal.map {
        case Some(_) =>
          Some(
            div(
              styleAttr := "display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;",
              // Date Navigator
              div(
                styleAttr := "display: flex; align-items: center; gap: 0.4rem;",
                slButton(
                  slSize    := "small",
                  slVariant := "neutral",
                  slIcon(slName := "chevron-left"),
                  onClick --> (_ => AppState.setPreviousDay())
                ),
                slButton(
                  slSize    := "small",
                  slVariant := "default",
                  child.text <-- AppState.selectedDate.signal.map { d =>
                    if d == DateUtils.today() then s"Today, $d" else d.toString
                  },
                  onClick --> (_ => AppState.setToday())
                ),
                slButton(
                  slSize    := "small",
                  slVariant := "neutral",
                  slIcon(slName := "chevron-right"),
                  onClick --> (_ => AppState.setNextDay())
                ),
                input(
                  tpe := "date",
                  cls := "date-picker-input",
                  styleAttr := "padding: 0.25rem 0.5rem; border: 1px solid var(--sl-color-neutral-300); border-radius: var(--sl-border-radius-medium); font-size: 0.85rem; background: var(--sl-input-background-color); color: var(--sl-color-neutral-900);",
                  value <-- AppState.selectedDate.signal.map(_.toString),
                  onChange.mapToValue --> { v =>
                    Option(v).filter(_.nonEmpty).foreach { str =>
                      try AppState.setDate(LocalDate.parse(str))
                      catch case _: Exception => ()
                    }
                  }
                )
              ),
              // Navigation Tabs (Dashboard vs Weight History)
              slButtonGroup(
                slButton(
                  slSize := "small",
                  slVariant <-- AppState.activeTab.signal.map(t => if t == "dashboard" then "primary" else "default"),
                  slIcon(slName := "calendar3", styleAttr := "margin-right: 0.35rem;"),
                  "Dashboard",
                  onClick --> (_ => AppState.activeTab.set("dashboard"))
                ),
                slButton(
                  slSize := "small",
                  slVariant <-- AppState.activeTab.signal.map(t => if t == "weights" then "primary" else "default"),
                  slIcon(slName := "speedometer2", styleAttr := "margin-right: 0.35rem;"),
                  "Weight History",
                  onClick --> { _ =>
                    AppState.activeTab.set("weights")
                    AppState.loadWeights()
                  }
                )
              )
            )
          )
        case None => None
      },

      // Right Section: Theme Toggle & User Profile
      div(
        styleAttr := "display: flex; align-items: center; gap: 0.75rem;",
        // Theme switch
        slButton(
          slSize    := "small",
          slVariant := "neutral",
          child <-- AppState.theme.signal.map { t =>
            if t == "dark" then slIcon(slName := "sun") else slIcon(slName := "moon")
          },
          onClick --> (_ => AppState.toggleTheme())
        ),

        // Auth state
        child <-- AppState.currentUser.signal.map {
          case Some(u) =>
            div(
              styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
              slAvatar(
                styleAttr  := "font-size: 1.8rem;",
                slImage    := u.pictureUrl.getOrElse(""),
                slInitials := u.name.take(2).toUpperCase
              ),
              span(
                styleAttr := "font-size: 0.9rem; font-weight: 500; color: var(--sl-color-neutral-800);",
                u.name
              ),
              slButton(
                slSize    := "small",
                slVariant := "neutral",
                slIcon(slName := "gear", slSlot := "prefix"),
                "Settings",
                onClick --> (_ => AppState.isSettingsOpen.set(true))
              ),
              slButton(
                slSize    := "small",
                slVariant := "neutral",
                "Sign Out",
                onClick --> { _ =>
                  import scala.concurrent.ExecutionContext.Implicits.global
                  ApiClient.logout().foreach { _ =>
                    AppState.currentUser.set(None)
                    AppState.notify("Signed out successfully", "neutral")
                  }
                }
              )
            )
          case None =>
            slButton(
              slSize    := "small",
              slVariant := "primary",
              slIcon(slName := "google", slSlot := "prefix"),
              "Sign in with Google",
              onClick --> { _ =>
                import scala.concurrent.ExecutionContext.Implicits.global
                ApiClient.getLoginUrl().foreach { url =>
                  org.scalajs.dom.window.location.href = url
                }
              }
            )
        }
      )
    )
