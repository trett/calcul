package com.calcul

import com.raquo.laminar.api.L.*
import scala.concurrent.ExecutionContext.Implicits.global
import com.calcul.ShoelaceDSL.*
import com.calcul.model.SaveGeminiKeyRequest

object SettingsView:

  def apply(): HtmlElement =
    val newKeyVar    = Var("")
    val errorMessage = Var(Option.empty[String])
    val isSubmitting = Var(false)

    slDialog(
      slLabel := "User Settings & Gemini API Key",
      slOpen <-- AppState.isSettingsOpen.signal,
      onSlRequestClose --> (_ => AppState.isSettingsOpen.set(false)),
      div(
        styleAttr := "display: flex; flex-direction: column; gap: 1.25rem;",

        // Account Details Section
        child.maybe <-- AppState.currentUser.signal.map {
          case Some(u) =>
            Some(
              div(
                styleAttr := "display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem; background-color: var(--sl-color-neutral-50); border-radius: var(--sl-border-radius-medium); border: 1px solid var(--sl-color-neutral-200);",
                slAvatar(
                  styleAttr  := "font-size: 2rem;",
                  slImage    := u.pictureUrl.getOrElse(""),
                  slInitials := u.name.take(2).toUpperCase
                ),
                div(
                  div(styleAttr := "font-weight: 600; font-size: 1rem;", u.name),
                  div(styleAttr := "font-size: 0.85rem; color: var(--sl-color-neutral-500);", u.email)
                )
              )
            )
          case None => None
        },
        slDivider(),

        // Current Gemini Key Status
        div(
          h4(styleAttr := "margin: 0 0 0.5rem 0; font-size: 1rem;", "Gemini API Key Status"),
          child <-- AppState.currentUser.signal.map {
            case Some(u) if u.hasGeminiKey =>
              div(
                styleAttr := "display: flex; align-items: center; justify-content: space-between; background-color: var(--sl-color-success-50); border: 1px solid var(--sl-color-success-200); padding: 0.75rem 1rem; border-radius: var(--sl-border-radius-medium);",
                div(
                  styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
                  slTag(slVariant := "success", slPill := true, "Active"),
                  span(
                    styleAttr := "font-family: monospace; font-size: 0.95rem; font-weight: 600; color: var(--sl-color-neutral-800);",
                    u.maskedGeminiKey.getOrElse("••••••••••••")
                  )
                ),
                slButton(
                  slVariant := "danger",
                  slSize    := "small",
                  slOutline := true,
                  slIcon(slName := "trash", slSlot := "prefix"),
                  "Remove Key",
                  onClick --> { _ =>
                    ApiClient.deleteGeminiKey().foreach { _ =>
                      AppState.currentUser.update(_.map(_.copy(hasGeminiKey = false, maskedGeminiKey = None)))
                      AppState.notify("Gemini API key removed", "neutral")
                    }
                  }
                )
              )
            case _ =>
              slAlert(
                slOpen    := true,
                slVariant := "warning",
                slIcon(slName := "exclamation-triangle", slSlot := "icon"),
                "No API key configured. You must provide a valid Gemini API key to use AI meal analysis."
              )
          }
        ),

        // Update / Add Gemini Key Form
        div(
          h4(styleAttr := "margin: 0 0 0.5rem 0; font-size: 1rem;", "Update API Key"),
          p(
            styleAttr := "font-size: 0.85rem; color: var(--sl-color-neutral-600); margin: 0 0 0.75rem 0; line-height: 1.4;",
            "Provide your Google Gemini Flash API key. The key is validated with Gemini before saving and stored encrypted at rest with AES-256-GCM."
          ),
          slInput(
            slType           := "password",
            slPasswordToggle := true,
            slLabel          := "Gemini API Key",
            slPlaceholder    := "AIzaSy...",
            slClearable      := true,
            slValue <-- newKeyVar.signal,
            onInput.mapToValue --> newKeyVar.writer
          ),
          div(
            styleAttr := "margin-top: 0.5rem; font-size: 0.8rem;",
            a(
              href   := "https://aistudio.google.com/app/apikey",
              target := "_blank",
              styleAttr := "color: var(--sl-color-primary-600); text-decoration: none; display: inline-flex; align-items: center; gap: 0.25rem;",
              slIcon(slName := "box-arrow-up-right"),
              "Get a free Gemini API key from Google AI Studio"
            )
          )
        ),

        // Error message alert if validation fails
        child.maybe <-- errorMessage.signal.map {
          case Some(err) =>
            Some(
              slAlert(
                slOpen    := true,
                slVariant := "danger",
                slIcon(slName := "x-circle", slSlot := "icon"),
                span(err)
              )
            )
          case None => None
        }
      ),

      // Dialog Actions
      div(
        slSlot    := "footer",
        styleAttr := "display: flex; justify-content: flex-end; gap: 0.5rem;",
        slButton(
          slVariant := "neutral",
          "Close",
          onClick --> (_ => AppState.isSettingsOpen.set(false))
        ),
        slButton(
          slVariant := "primary",
          slLoading <-- isSubmitting.signal,
          slIcon(slName := "check-circle", slSlot := "prefix"),
          "Validate & Save Key",
          onClick --> { _ =>
            val key = newKeyVar.now().trim
            if key.isEmpty then errorMessage.set(Some("Please enter an API key before saving."))
            else
              isSubmitting.set(true)
              errorMessage.set(None)
              ApiClient.saveGeminiKey(SaveGeminiKeyRequest(key)).foreach { status =>
                isSubmitting.set(false)
                AppState.currentUser.update(_.map(_.copy(hasGeminiKey = true, maskedGeminiKey = status.maskedKey)))
                newKeyVar.set("")
                AppState.notify("Gemini API key validated and saved successfully!", "success")
                AppState.isSettingsOpen.set(false)
              }
          }
        )
      )
    )
