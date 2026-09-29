package ru.trett.calcul

import com.raquo.laminar.api.L.*
import ru.trett.calcul.ShoelaceDSL.*
import ru.trett.calcul.model.SaveGeminiKeyRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object SettingsView:

  def apply(): HtmlElement =
    val newKeyVar    = Var(AppState.currentUser.now().flatMap(_.maskedGeminiKey).getOrElse(""))
    val errorMessage = Var(Option.empty[String])
    val isSubmitting = Var(false)
    val isDeleting   = Var(false)

    def closeDialog(): Unit =
      AppState.isSettingsOpen.set(false)
      errorMessage.set(None)

    val canRemoveSignal = AppState.currentUser.signal.combineWith(newKeyVar.signal).map { case (u, text) =>
      u.exists(_.hasGeminiKey) || text.trim.nonEmpty
    }

    slDialog(
      slLabel   := "User Settings & Gemini API Key",
      styleAttr := "--width: min(500px, calc(100vw - 2rem));",
      slOpen <-- AppState.isSettingsOpen.signal,
      onSlRequestClose --> (_ => closeDialog()),
      // Sync textfield value whenever settings dialog is opened
      AppState.isSettingsOpen.signal --> { isOpen =>
        if isOpen then
          newKeyVar.set(AppState.currentUser.now().flatMap(_.maskedGeminiKey).getOrElse(""))
          errorMessage.set(None)
      },
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
                  div(
                    styleAttr := "font-size: 0.85rem; color: var(--sl-color-neutral-500); word-break: break-all;",
                    u.email
                  )
                )
              )
            )
          case None => None
        },
        slDivider(),

        // Gemini API Key Section
        div(
          h4(styleAttr := "margin: 0 0 0.5rem 0; font-size: 1rem;", "Gemini API Key"),
          p(
            styleAttr := "font-size: 0.85rem; color: var(--sl-color-neutral-600); margin: 0 0 0.75rem 0; line-height: 1.4;",
            "Provide your Gemini API key to enable AI meal analysis. The key is validated before saving and securely encrypted at rest."
          ),
          slInput(
            slType           := "password",
            slPasswordToggle := true,
            slLabel          := "API Key",
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
          ),
          p(
            styleAttr := "font-size: 0.75rem; color: var(--sl-color-neutral-500); margin: 0.35rem 0 0 0;",
            "Ensure you use a Gemini API key (starts with AIzaSy...), not a Google OAuth Client ID or Secret."
          )
        ),

        // Error message alert if validation or deletion fails
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
        styleAttr := "display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; width: 100%;",
        slButton(
          slVariant := "danger",
          slOutline := true,
          slLoading <-- isDeleting.signal,
          slDisabled <-- canRemoveSignal.map(!_),
          slIcon(slName := "trash", slSlot := "prefix"),
          "Remove",
          onClick --> { _ =>
            isDeleting.set(true)
            errorMessage.set(None)
            ApiClient.deleteGeminiKey().onComplete {
              case Success(_) =>
                isDeleting.set(false)
                newKeyVar.set("")
                AppState.currentUser.update(_.map(_.copy(hasGeminiKey = false, maskedGeminiKey = None)))
                AppState.notify("Gemini API key removed", "neutral")
              case Failure(err) =>
                isDeleting.set(false)
                val msg = err.getMessage
                errorMessage.set(Some(s"Failed to remove API key: $msg"))
                AppState.notify(s"Failed to remove API key: $msg", "danger")
            }
          }
        ),
        div(
          styleAttr := "display: flex; gap: 0.5rem;",
          slButton(
            slVariant := "neutral",
            "Close",
            onClick --> (_ => closeDialog())
          ),
          slButton(
            slVariant := "primary",
            slLoading <-- isSubmitting.signal,
            slIcon(slName := "check-circle", slSlot := "prefix"),
            "Save",
            onClick --> { _ =>
              val key = newKeyVar.now().trim
              if key.isEmpty then errorMessage.set(Some("Please enter an API key before saving."))
              else if key.contains('•') then
                errorMessage.set(Some("This API key is already saved. Enter a new key to update."))
              else
                isSubmitting.set(true)
                errorMessage.set(None)
                ApiClient.saveGeminiKey(SaveGeminiKeyRequest(key)).onComplete {
                  case Success(status) =>
                    isSubmitting.set(false)
                    AppState.currentUser.update(_.map(_.copy(hasGeminiKey = true, maskedGeminiKey = status.maskedKey)))
                    newKeyVar.set(status.maskedKey.getOrElse(key))
                    AppState.notify("Gemini API key validated and saved successfully!", "success")
                  case Failure(err) =>
                    isSubmitting.set(false)
                    val msg = err.getMessage
                    val cleanMsg =
                      if msg.startsWith("Invalid Gemini API key:") then msg
                      else s"Key validation failed: $msg"
                    errorMessage.set(Some(cleanMsg))
                }
            }
          )
        )
      )
    )
