package com.calcul

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global
import com.calcul.ShoelaceDSL.*

object LandingView:

  def apply(): HtmlElement =
    div(
      cls       := "landing-container",
      styleAttr := "max-width: 900px; margin: 2rem auto; padding: 1.5rem; text-align: center;",

      // Hero Section
      div(
        styleAttr := "margin-bottom: 3.5rem;",
        div(
          styleAttr := "display: inline-flex; align-items: center; gap: 0.5rem; margin-bottom: 1.25rem;",
          slIcon(slName := "fire", styleAttr := "font-size: 2.75rem; color: var(--sl-color-primary-600);"),
          span(
            styleAttr := "font-size: 2.5rem; font-weight: 800; letter-spacing: -1px; color: var(--sl-color-neutral-900);",
            "CalTrack"
          ),
          span(
            styleAttr := "background-color: var(--sl-color-primary-50); color: var(--sl-color-primary-700); font-size: 1.1rem; font-weight: 700; padding: 0.25rem 0.65rem; border-radius: 9999px; border: 1px solid var(--sl-color-primary-200);",
            "AI"
          )
        ),
        h1(
          styleAttr := "font-size: 2.25rem; font-weight: 800; line-height: 1.25; margin: 0 0 1rem 0; color: var(--sl-color-neutral-900);",
          "Smart Calorie & Nutrition Tracking Powered by AI"
        ),
        p(
          styleAttr := "font-size: 1.15rem; color: var(--sl-color-neutral-600); max-width: 650px; margin: 0 auto 2rem auto; line-height: 1.6;",
          "Log your meals in seconds with photo and description analysis powered by Google Gemini Flash. Keep your data private by using your own Gemini API key."
        ),
        div(
          styleAttr := "display: flex; justify-content: center; gap: 1rem; align-items: center;",
          slButton(
            slSize    := "large",
            slVariant := "primary",
            slIcon(slName := "google", slSlot := "prefix"),
            "Sign in with Google",
            onClick --> { _ =>
              ApiClient.getLoginUrl().foreach { url =>
                dom.window.location.href = url
              }
            }
          )
        ),
        p(
          styleAttr := "font-size: 0.85rem; color: var(--sl-color-neutral-400); margin-top: 0.75rem;",
          "Authentication required to access dashboard and tracking features."
        )
      ),

      // Feature Highlights Grid
      div(
        styleAttr := "display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 1.5rem; text-align: left; margin-bottom: 3rem;",
        slCard(
          div(
            slSlot    := "header",
            styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
            slIcon(slName  := "stars", styleAttr := "font-size: 1.4rem; color: var(--sl-color-primary-600);"),
            span(styleAttr := "font-weight: 600; font-size: 1.1rem;", "AI Meal Analysis")
          ),
          p(
            styleAttr := "color: var(--sl-color-neutral-600); font-size: 0.95rem; line-height: 1.5; margin: 0;",
            "Snap a picture or type what you ate. Gemini Flash extracts items, estimates calories, and provides nutritional explanations instantly."
          )
        ),
        slCard(
          div(
            slSlot    := "header",
            styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
            slIcon(slName  := "key", styleAttr := "font-size: 1.4rem; color: var(--sl-color-primary-600);"),
            span(styleAttr := "font-weight: 600; font-size: 1.1rem;", "Bring Your Own Key")
          ),
          p(
            styleAttr := "color: var(--sl-color-neutral-600); font-size: 0.95rem; line-height: 1.5; margin: 0;",
            "Use your personal Gemini API key. Your key is validated, encrypted with AES-256-GCM at rest, and never exposed to other users."
          )
        ),
        slCard(
          div(
            slSlot    := "header",
            styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
            slIcon(slName  := "graph-up", styleAttr := "font-size: 1.4rem; color: var(--sl-color-primary-600);"),
            span(styleAttr := "font-weight: 600; font-size: 1.1rem;", "Goals & Weigh-in Trends")
          ),
          p(
            styleAttr := "color: var(--sl-color-neutral-600); font-size: 0.95rem; line-height: 1.5; margin: 0;",
            "Set daily calorie targets, monitor calorie balances, track body weight trends, and maintain consistency with clear visual indicators."
          )
        )
      )
    )
