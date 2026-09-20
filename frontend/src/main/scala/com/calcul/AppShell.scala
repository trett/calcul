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

      // Main Content Area
      mainTag(
        cls       := "app-content",
        styleAttr := "flex: 1; max-width: 1100px; width: 100%; margin: 0 auto; padding: 1.5rem 1rem;",
        contentView
      ),

      // Footer
      footerTag(
        cls := "app-footer",
        styleAttr := "text-align: center; padding: 1.25rem; font-size: 0.85rem; color: var(--sl-color-neutral-500); border-top: 1px solid var(--sl-color-neutral-200); background-color: var(--sl-panel-background-color);",
        p(
          margin := "0",
          "CalTrack AI — Direct-Style Scala 3 with SoftwareMill Ox, Tapir & Laminar"
        )
      )
    )
