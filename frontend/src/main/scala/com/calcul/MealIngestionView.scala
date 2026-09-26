package com.calcul

import java.util.concurrent.atomic.AtomicInteger
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import java.time.LocalDate
import com.calcul.ShoelaceDSL.*
import com.calcul.model.{AnalyzeMealRequest, CreateMealItem, CreateMealRequest}

object MealIngestionView:

  final case class EditableItem(id: Int, name: Var[String], calories: Var[Int])

  def canAnalyze(userOpt: Option[com.calcul.model.UserSummary]): Boolean =
    userOpt.exists(_.hasGeminiKey)

  def apply(): HtmlElement =
    val descriptionVar         = Var("")
    val selectedFileNameVar    = Var(Option.empty[String])
    val selectedImageBase64Var = Var(Option.empty[String])
    val selectedImageMimeVar   = Var(Option.empty[String])
    val isAnalyzingVar         = Var(false)
    val isSavingVar            = Var(false)

    // Modal state
    val reviewModalOpenVar   = Var(false)
    val reviewExplanationVar = Var("")
    val reviewItemsVar       = Var(List.empty[EditableItem])
    val nextItemId           = new AtomicInteger(1)

    def runAnalysis(): Unit =
      if !canAnalyze(AppState.currentUser.now()) then
        AppState.notify("Please configure your Gemini API key in User Settings to analyze meals.", "warning")
        AppState.isSettingsOpen.set(true)
      else
        val desc    = descriptionVar.now().trim
        val b64Opt  = selectedImageBase64Var.now()
        val mimeOpt = selectedImageMimeVar.now()

        if desc.isEmpty && b64Opt.isEmpty then
          AppState.notify("Please enter a meal description or select an image", "warning")
        else
          isAnalyzingVar.set(true)
          val req = AnalyzeMealRequest(
            description = if desc.nonEmpty then Some(desc) else None,
            imageBase64 = b64Opt,
            mimeType = mimeOpt
          )
          ApiClient.analyzeMeal(req).onComplete {
            case Success(analysis) =>
              isAnalyzingVar.set(false)
              reviewExplanationVar.set(analysis.explanation)
              val items = analysis.items.zipWithIndex.map { case (item, idx) =>
                EditableItem(idx + 1, Var(item.name), Var(item.calories))
              }
              nextItemId.set(items.length + 1)
              reviewItemsVar.set(items)
              reviewModalOpenVar.set(true)
            case Failure(err) =>
              isAnalyzingVar.set(false)
              AppState.notify(s"AI Analysis failed: ${err.getMessage}", "danger")
          }

    def saveMeal(): Unit =
      val items = reviewItemsVar.now().map { it =>
        CreateMealItem(it.name.now(), it.calories.now())
      }
      val totalCal = items.map(_.estimatedCalories).sum
      val req = CreateMealRequest(
        mealDate = AppState.selectedDate.now(),
        description = if descriptionVar.now().nonEmpty then descriptionVar.now() else "Logged meal",
        totalCalories = totalCal,
        aiExplanation = reviewExplanationVar.now(),
        items = items
      )
      isSavingVar.set(true)
      ApiClient.createMeal(req).onComplete {
        case Success(_) =>
          isSavingVar.set(false)
          reviewModalOpenVar.set(false)
          descriptionVar.set("")
          selectedFileNameVar.set(None)
          selectedImageBase64Var.set(None)
          selectedImageMimeVar.set(None)
          AppState.notify("Meal logged successfully!", "success")
          AppState.loadDailyData()
        case Failure(err) =>
          isSavingVar.set(false)
          AppState.notify(s"Failed to save meal: ${err.getMessage}", "danger")
      }

    div(
      cls       := "meal-ingestion-section",
      styleAttr := "display: flex; flex-direction: column; gap: 1rem;",

      // Card for meal input
      slCard(
        styleAttr := "width: 100%; border-radius: var(--sl-border-radius-large); box-shadow: var(--sl-shadow-small);",

        // Card Header
        div(
          slSlot    := "header",
          styleAttr := "display: flex; align-items: center; justify-content: space-between;",
          div(
            styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
            slIcon(slName  := "stars", styleAttr := "color: var(--sl-color-primary-600); font-size: 1.25rem;"),
            span(styleAttr := "font-weight: 600; font-size: 1.05rem;", "Log Meal with AI")
          ),
          span(
            styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-500);",
            "Powered by Gemini Flash"
          )
        ),

        // Card Body
        div(
          styleAttr := "display: flex; flex-direction: column; gap: 0.9rem;",

          // Description input
          textArea(
            cls := "meal-description-input",
            placeholder := "What did you eat? E.g. 'Grilled salmon with quinoa and asparagus, glass of sparkling water'",
            styleAttr := "box-sizing: border-box; width: 100%; max-width: 100%; min-height: 80px; padding: 0.75rem; border: 1px solid var(--sl-color-neutral-300); border-radius: var(--sl-border-radius-medium); font-family: inherit; font-size: 0.95rem; resize: vertical; background: var(--sl-input-background-color); color: var(--sl-color-neutral-900);",
            controlled(
              value <-- descriptionVar.signal,
              onInput.mapToValue --> descriptionVar.writer
            )
          ),

          // File upload / camera capture row
          div(
            styleAttr := "display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0.75rem;",
            label(
              styleAttr := "display: inline-flex; align-items: center; gap: 0.4rem; cursor: pointer; padding: 0.4rem 0.8rem; border: 1px dashed var(--sl-color-neutral-400); border-radius: var(--sl-border-radius-medium); font-size: 0.85rem; color: var(--sl-color-neutral-700);",
              slIcon(slName := "camera", styleAttr := "font-size: 1rem;"),
              span(
                child.text <-- selectedFileNameVar.signal.map {
                  case Some(name) => s"Photo: $name"
                  case None       => "Attach food photo"
                }
              ),
              input(
                tpe       := "file",
                accept    := "image/*",
                styleAttr := "display: none;",
                onChange --> { (e: dom.Event) =>
                  val target = e.target.asInstanceOf[dom.HTMLInputElement]
                  Option(target.files).filter(_.length > 0).foreach { files =>
                    val file = files(0)
                    selectedFileNameVar.set(Some(file.name))
                    val reader = new dom.FileReader()
                    reader.onload = (_: dom.Event) =>
                      val dataUrl = reader.result.asInstanceOf[String]
                      val parts   = dataUrl.split(",", 2)
                      val mime =
                        if parts.length == 2 && parts(0).contains(":") && parts(0).contains(";") then
                          parts(0).substring(parts(0).indexOf(":") + 1, parts(0).indexOf(";"))
                        else "image/jpeg"
                      val b64 = if parts.length == 2 then parts(1) else dataUrl
                      selectedImageMimeVar.set(Some(mime))
                      selectedImageBase64Var.set(Some(b64))
                    reader.readAsDataURL(file)
                  }
                }
              )
            ),

            // Analyze button
            slButton(
              slVariant := "primary",
              slLoading <-- isAnalyzingVar.signal,
              slDisabled <-- isAnalyzingVar.signal,
              slIcon(slName := "sparkles", slSlot := "prefix"),
              "Analyze with AI",
              onClick --> (_ => runAnalysis())
            )
          )
        )
      ),

      // Review & Confirmation Modal Dialog
      slDialog(
        slOpen <-- reviewModalOpenVar.signal,
        slLabel   := "Review Meal Breakdown",
        styleAttr := "--width: 620px;",
        div(
          styleAttr := "display: flex; flex-direction: column; gap: 1rem;",

          // AI Explanation Alert
          div(
            styleAttr := "background-color: var(--sl-color-primary-50); border-left: 4px solid var(--sl-color-primary-600); padding: 0.75rem 1rem; border-radius: var(--sl-border-radius-medium); font-size: 0.9rem; color: var(--sl-color-neutral-800);",
            div(styleAttr := "font-weight: 600; margin-bottom: 0.25rem;", "AI Nutritional Estimate"),
            p(
              styleAttr := "margin: 0;",
              child.text <-- reviewExplanationVar.signal
            )
          ),

          // Date selection
          div(
            styleAttr := "display: flex; align-items: center; justify-content: space-between;",
            span(styleAttr := "font-weight: 500; font-size: 0.9rem;", "Date:"),
            input(
              tpe := "date",
              styleAttr := "padding: 0.3rem 0.5rem; border: 1px solid var(--sl-color-neutral-300); border-radius: var(--sl-border-radius-medium);",
              value <-- AppState.selectedDate.signal.map(_.toString),
              onChange.mapToValue --> { v =>
                Option(v).filter(_.nonEmpty).foreach { str =>
                  try AppState.setDate(LocalDate.parse(str))
                  catch case _: Exception => ()
                }
              }
            )
          ),

          // Items Table
          div(
            styleAttr := "display: flex; flex-direction: column; gap: 0.5rem;",
            span(styleAttr := "font-weight: 600; font-size: 0.95rem;", "Food Items & Calories"),
            children <-- reviewItemsVar.signal.map { items =>
              items.map { item =>
                div(
                  styleAttr := "display: flex; align-items: center; gap: 0.5rem; padding: 0.4rem; background-color: var(--sl-color-neutral-100); border-radius: var(--sl-border-radius-medium);",
                  input(
                    tpe         := "text",
                    placeholder := "Item name",
                    styleAttr := "flex: 2; padding: 0.35rem 0.6rem; border: 1px solid var(--sl-color-neutral-300); border-radius: var(--sl-border-radius-medium); font-size: 0.9rem;",
                    controlled(
                      value <-- item.name.signal,
                      onInput.mapToValue --> item.name.writer
                    )
                  ),
                  input(
                    tpe         := "number",
                    placeholder := "kcal",
                    styleAttr := "flex: 1; max-width: 100px; padding: 0.35rem 0.6rem; border: 1px solid var(--sl-color-neutral-300); border-radius: var(--sl-border-radius-medium); font-size: 0.9rem;",
                    controlled(
                      value <-- item.calories.signal.map(_.toString),
                      onInput.mapToValue --> { v =>
                        val n =
                          try v.toInt
                          catch case _: Exception => 0
                        item.calories.set(n)
                      }
                    )
                  ),
                  span(styleAttr := "font-size: 0.85rem; color: var(--sl-color-neutral-600);", "kcal"),
                  slButton(
                    slSize    := "small",
                    slVariant := "neutral",
                    slIcon(slName := "trash"),
                    onClick --> { _ =>
                      reviewItemsVar.update(_.filterNot(_.id == item.id))
                    }
                  )
                )
              }
            },

            // Add item button
            div(
              styleAttr := "display: flex; justify-content: flex-start; margin-top: 0.25rem;",
              slButton(
                slSize    := "small",
                slVariant := "default",
                slIcon(slName := "plus-lg", slSlot := "prefix"),
                "Add Item",
                onClick --> { _ =>
                  val newItem = EditableItem(nextItemId.getAndIncrement(), Var("Custom Item"), Var(100))
                  reviewItemsVar.update(_ :+ newItem)
                }
              )
            )
          ),

          // Total Calories Bar
          div(
            styleAttr := "display: flex; align-items: center; justify-content: space-between; padding: 0.75rem 1rem; background-color: var(--sl-color-neutral-100); border-radius: var(--sl-border-radius-medium); font-weight: 700; font-size: 1.05rem;",
            span("Estimated Total:"),
            span(
              styleAttr := "color: var(--sl-color-primary-700);",
              child.text <-- reviewItemsVar.signal.flatMapSwitch { items =>
                val signals = items.map(_.calories.signal)
                if signals.isEmpty then Val("0 kcal")
                else Signal.combineSeq(signals).map(cals => s"${cals.sum} kcal")
              }
            )
          )
        ),

        // Dialog Footer
        div(
          slSlot    := "footer",
          styleAttr := "display: flex; justify-content: flex-end; gap: 0.5rem;",
          slButton(
            slVariant := "neutral",
            "Cancel",
            onClick --> (_ => reviewModalOpenVar.set(false))
          ),
          slButton(
            slVariant := "primary",
            slLoading <-- isSavingVar.signal,
            slDisabled <-- isSavingVar.signal,
            slIcon(slName := "check2", slSlot := "prefix"),
            "Save Meal",
            onClick --> (_ => saveMeal())
          )
        )
      )
    )
