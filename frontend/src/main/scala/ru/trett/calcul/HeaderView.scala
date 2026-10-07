package ru.trett.calcul

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import ru.trett.calcul.ShoelaceDSL.*
import ru.trett.calcul.model.UserSummary

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

object HeaderView:

  def apply(): HtmlElement =
    headerTag(
      cls := "app-header",

      // Brand / Logo
      div(
        cls := "header-brand",
        onClick --> (_ => AppState.activeTab.set("dashboard")),
        slIcon(slName := "fire", styleAttr := "font-size: 1.8rem; color: var(--sl-color-primary-600);"),
        span(cls      := "header-brand-name", "CalTrack"),
        span(cls      := "header-brand-badge", "AI")
      ),

      // Authenticated Navigation: Date Navigator
      child.maybe <-- AppState.currentUser.signal.map {
        case Some(_) =>
          Some(
            div(
              cls := "header-nav",
              div(
                cls := "date-navigator",
                slButton(
                  slSize    := "small",
                  slVariant := "neutral",
                  title     := "Previous day",
                  slIcon(slName := "chevron-left"),
                  onClick --> (_ => AppState.setPreviousDay())
                ),
                datePill(),
                slButton(
                  slSize    := "small",
                  slVariant := "neutral",
                  title     := "Next day",
                  slIcon(slName := "chevron-right"),
                  onClick --> (_ => AppState.setNextDay())
                )
              )
            )
          )
        case None => None
      },

      // Right Section: Theme Toggle & User Profile
      div(
        cls := "header-actions",
        slButton(
          slSize    := "small",
          slVariant := "neutral",
          title     := "Toggle theme",
          child <-- AppState.theme.signal.map { t =>
            if t == "dark" then slIcon(slName := "sun") else slIcon(slName := "moon")
          },
          onClick --> (_ => AppState.toggleTheme())
        ),

        // Auth state
        child <-- AppState.currentUser.signal.map {
          case Some(user) => userMenu(user)
          case None =>
            slButton(
              slSize    := "small",
              slVariant := "primary",
              slIcon(slName := "google", slSlot := "prefix"),
              "Sign in with Google",
              onClick --> { _ =>
                ApiClient.getLoginUrl().foreach { url =>
                  dom.window.location.href = url
                }
              }
            )
        }
      )
    )

  // Date control styled as a pill; the native date picker is overlaid invisibly
  // so tapping the pill opens the picker without a second visible input.
  private def datePill(): HtmlElement =
    label(
      cls   := "date-pill",
      title := "Pick a date",
      slIcon(slName := "calendar3"),
      span(
        child.text <-- AppState.selectedDate.signal.map { d =>
          if d == DateUtils.today() then "Today" else DateUtils.formatFriendlyDate(d)
        }
      ),
      input(
        tpe := "date",
        cls := "date-picker-input",
        value <-- AppState.selectedDate.signal.map(_.toString),
        onChange.mapToValue --> { v =>
          Option(v).filter(_.nonEmpty).foreach { str =>
            try AppState.setDate(LocalDate.parse(str))
            catch case _: Exception => ()
          }
        }
      )
    )

  // Avatar acts as a dropdown trigger exposing account actions.
  private def userMenu(user: UserSummary): HtmlElement =
    slDropdown(
      cls         := "header-user-menu",
      slPlacement := "bottom-end",
      slAvatar(
        slSlot     := "trigger",
        styleAttr  := "font-size: 1.7rem; flex-shrink: 0; cursor: pointer;",
        slImage    := user.pictureUrl.getOrElse(""),
        slInitials := user.name.take(2).toUpperCase
      ),
      slMenu(
        slMenuItem(
          slIcon(slName := "gear", slSlot := "prefix"),
          "Settings",
          onClick --> (_ => AppState.isSettingsOpen.set(true))
        ),
        slMenuItem(
          slIcon(slName := "box-arrow-right", slSlot := "prefix"),
          "Sign Out",
          onClick --> { _ =>
            ApiClient.logout().foreach { _ =>
              AppState.currentUser.set(None)
              AppState.notify("Signed out successfully", "neutral")
            }
          }
        )
      ),
      onSlSelect --> { ev =>
        ev.currentTarget.asInstanceOf[js.Dynamic].hide()
      }
    )
