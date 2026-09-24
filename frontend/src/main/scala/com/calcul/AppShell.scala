package com.calcul

import com.raquo.laminar.api.L.*
import com.calcul.ShoelaceDSL.*

object AppShell:

  def apply(contentView: HtmlElement): HtmlElement =
    div(
      cls := "app-container",
      styleAttr := "min-height: 100vh; display: flex; flex-direction: column; background-color: var(--sl-color-neutral-50); color: var(--sl-color-neutral-900); font-family: var(--sl-font-sans);",

      // Top notification toast/alert
      child.maybe <-- AppState.notification.signal.map {
        case Some((variant, msg)) =>
          Some(
            div(
              styleAttr := "position: fixed; top: 1rem; right: 1rem; z-index: 1000; min-width: 280px; box-shadow: var(--sl-shadow-large); border-radius: var(--sl-border-radius-medium);",
              slAlert(
                slOpen    := true,
                slVariant := variant,
                slIcon(slName := "info-circle", slSlot := "icon"),
                span(msg)
              )
            )
          )
        case None => None
      },

      // Navigation Header
      HeaderView(),

      // Missing Gemini Key Warning Banner
      child.maybe <-- AppState.currentUser.signal.map {
        case Some(user) if !user.hasGeminiKey =>
          Some(
            div(
              cls       := "gemini-key-warning-banner",
              styleAttr := "max-width: 1100px; width: 100%; margin: 1rem auto 0 auto; padding: 0 1rem;",
              slAlert(
                slOpen    := true,
                slVariant := "warning",
                slIcon(slName := "exclamation-triangle", slSlot := "icon"),
                div(
                  styleAttr := "display: flex; align-items: center; justify-content: space-between; width: 100%; flex-wrap: wrap; gap: 0.5rem;",
                  span(
                    styleAttr := "font-size: 0.95rem; font-weight: 500;",
                    "Gemini API key required. Please configure your key in User Settings to enable AI meal analysis."
                  ),
                  slButton(
                    slSize    := "small",
                    slVariant := "warning",
                    slOutline := true,
                    slIcon(slName := "gear", slSlot := "prefix"),
                    "Configure Key",
                    onClick --> (_ => AppState.isSettingsOpen.set(true))
                  )
                )
              )
            )
          )
        case _ => None
      },

      // Main Content Area
      mainTag(
        cls       := "app-content",
        styleAttr := "flex: 1; max-width: 1100px; width: 100%; margin: 0 auto; padding: 1.5rem 1rem;",
        child <-- AppState.currentUser.signal.combineWith(AppState.isAuthChecking.signal).map {
          case (None, true) =>
            div(
              styleAttr := "display: flex; justify-content: center; align-items: center; min-height: 400px;",
              slSpinner(styleAttr := "font-size: 3rem;")
            )
          case (None, false) =>
            LandingView()
          case (Some(_), _) =>
            contentView
        }
      ),

      // Footer
      footerTag(
        cls := "app-footer",
        styleAttr := "text-align: center; padding: 1.25rem; font-size: 0.85rem; color: var(--sl-color-neutral-500); border-top: 1px solid var(--sl-color-neutral-200); background-color: var(--sl-panel-background-color);",
        p(
          margin := "0",
          "CalTrack AI — Direct-Style Scala 3 with SoftwareMill Ox, Tapir & Laminar"
        )
      ),

      // User Settings Dialog
      SettingsView()
    )
