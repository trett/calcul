package com.calcul

import com.raquo.laminar.api.L.*
import com.raquo.airstream.state.Var
import scala.concurrent.ExecutionContext.Implicits.global
import com.calcul.ShoelaceDSL.*
import com.calcul.model.SetTargetRequest

object DashboardView:

  def apply(): HtmlElement =
    val targetModalOpen = Var(false)
    val targetInput     = Var("2000")

    def saveTarget(): Unit =
      val t =
        try targetInput.now().toInt
        catch case _: Exception => 2000
      val req = SetTargetRequest(AppState.selectedDate.now(), t)
      ApiClient.setDailyTarget(req).onComplete {
        case scala.util.Success(_) =>
          targetModalOpen.set(false)
          AppState.notify(s"Daily target updated to $t kcal", "success")
          AppState.loadDailyData()
        case scala.util.Failure(err) =>
          AppState.notify(s"Failed to update target: ${err.getMessage}", "danger")
      }

    div(
      cls       := "dashboard-container",
      styleAttr := "display: flex; flex-direction: column; gap: 1.5rem;",

      // 1. Calorie Progress & Budget Card
      slCard(
        styleAttr := "width: 100%; border-radius: var(--sl-border-radius-large); box-shadow: var(--sl-shadow-small);",

        // Card Header
        div(
          slSlot    := "header",
          styleAttr := "display: flex; align-items: center; justify-content: space-between;",
          div(
            styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
            slIcon(slName  := "activity", styleAttr := "color: var(--sl-color-primary-600); font-size: 1.25rem;"),
            span(styleAttr := "font-weight: 600; font-size: 1.05rem;", "Calorie Budget")
          ),
          slButton(
            slSize    := "small",
            slVariant := "default",
            slIcon(slName := "pencil", slSlot := "prefix"),
            "Edit Target",
            onClick --> { _ =>
              val currentTarget = AppState.dailySummary.now().map(_.calorieTarget).getOrElse(2000)
              targetInput.set(currentTarget.toString)
              targetModalOpen.set(true)
            }
          )
        ),

        // Card Body: Stats and Progress Bar
        div(
          styleAttr := "display: flex; flex-direction: column; gap: 1rem;",

          // Key Metrics Grid
          div(
            styleAttr := "display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 1rem; text-align: center;",

            // Consumed
            div(
              styleAttr := "background: var(--sl-color-neutral-100); padding: 0.75rem; border-radius: var(--sl-border-radius-medium);",
              div(
                styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-600); text-transform: uppercase; font-weight: 600;",
                "Consumed"
              ),
              div(
                styleAttr := "font-size: 1.5rem; font-weight: 700; color: var(--sl-color-neutral-900);",
                child.text <-- AppState.dailySummary.signal.map {
                  case Some(s) => s"${s.totalConsumed}"
                  case None    => "0"
                },
                span(
                  styleAttr := "font-size: 0.85rem; font-weight: 500; color: var(--sl-color-neutral-500); margin-left: 0.2rem;",
                  "kcal"
                )
              )
            ),

            // Target
            div(
              styleAttr := "background: var(--sl-color-neutral-100); padding: 0.75rem; border-radius: var(--sl-border-radius-medium);",
              div(
                styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-600); text-transform: uppercase; font-weight: 600;",
                "Daily Target"
              ),
              div(
                styleAttr := "font-size: 1.5rem; font-weight: 700; color: var(--sl-color-neutral-900);",
                child.text <-- AppState.dailySummary.signal.map {
                  case Some(s) => s"${s.calorieTarget}"
                  case None    => "2000"
                },
                span(
                  styleAttr := "font-size: 0.85rem; font-weight: 500; color: var(--sl-color-neutral-500); margin-left: 0.2rem;",
                  "kcal"
                )
              )
            ),

            // Remaining / Status
            div(
              styleAttr := "background: var(--sl-color-neutral-100); padding: 0.75rem; border-radius: var(--sl-border-radius-medium);",
              div(
                styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-600); text-transform: uppercase; font-weight: 600;",
                "Remaining"
              ),
              div(
                styleAttr := "font-size: 1.5rem; font-weight: 700;",
                child <-- AppState.dailySummary.signal.map {
                  case Some(s) if s.remainingCalories >= 0 =>
                    span(styleAttr := "color: var(--sl-color-success-600);", s"${s.remainingCalories}")
                  case Some(s) =>
                    span(styleAttr := "color: var(--sl-color-danger-600);", s"+${Math.abs(s.remainingCalories)}")
                  case None =>
                    span(styleAttr := "color: var(--sl-color-neutral-500);", "2000")
                },
                span(
                  styleAttr := "font-size: 0.85rem; font-weight: 500; color: var(--sl-color-neutral-500); margin-left: 0.2rem;",
                  "kcal"
                )
              )
            )
          ),

          // Progress Bar
          div(
            styleAttr := "display: flex; flex-direction: column; gap: 0.35rem;",
            slProgressBar(
              styleAttr := "--indicator-color: var(--sl-color-primary-600); height: 1rem; border-radius: var(--sl-border-radius-pill);",
              slValueInt <-- AppState.dailySummary.signal.map {
                case Some(s) if s.calorieTarget > 0 =>
                  Math.min(100, (s.totalConsumed * 100) / s.calorieTarget)
                case _ => 0
              }
            ),
            div(
              styleAttr := "display: flex; justify-content: space-between; font-size: 0.75rem; color: var(--sl-color-neutral-500);",
              span("0 kcal"),
              child.text <-- AppState.dailySummary.signal.map {
                case Some(s) if s.calorieTarget > 0 =>
                  val pct = (s.totalConsumed * 100) / s.calorieTarget
                  s"$pct% of daily target"
                case _ => "0%"
              },
              child.text <-- AppState.dailySummary.signal.map {
                case Some(s) => s"${s.calorieTarget} kcal"
                case None    => "2000 kcal"
              }
            )
          )
        )
      ),

      // 2. Quick Meal Ingestion Widget
      MealIngestionView(),

      // 3. Meal Timeline Feed
      div(
        cls       := "meal-timeline-feed",
        styleAttr := "display: flex; flex-direction: column; gap: 1rem;",

        // Timeline Header
        div(
          styleAttr := "display: flex; align-items: center; justify-content: space-between; padding: 0 0.25rem;",
          div(
            styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
            slIcon(slName  := "clock-history", styleAttr := "font-size: 1.25rem; color: var(--sl-color-primary-600);"),
            span(styleAttr := "font-weight: 700; font-size: 1.1rem;", "Meal Timeline")
          ),
          slBadge(
            slVariant := "primary",
            slPill    := true,
            child.text <-- AppState.meals.signal.map(m => s"${m.length} logged")
          )
        ),

        // Meal Cards List or Empty State
        child <-- AppState.meals.signal.map { meals =>
          if meals.isEmpty then
            slCard(
              styleAttr := "width: 100%; text-align: center; padding: 2rem 1rem;",
              slIcon(
                slName    := "cup-hot",
                styleAttr := "font-size: 2.5rem; color: var(--sl-color-neutral-400); margin-bottom: 0.75rem;"
              ),
              p(
                styleAttr := "font-weight: 600; margin: 0 0 0.25rem 0; color: var(--sl-color-neutral-700);",
                "No meals logged yet today"
              ),
              p(
                styleAttr := "font-size: 0.85rem; color: var(--sl-color-neutral-500); margin: 0;",
                "Use the AI logger above to describe or upload a photo of what you ate!"
              )
            )
          else
            div(
              styleAttr := "display: flex; flex-direction: column; gap: 0.85rem;",
              meals.map { meal =>
                slCard(
                  styleAttr := "width: 100%; border-radius: var(--sl-border-radius-large); box-shadow: var(--sl-shadow-small);",

                  // Meal Card Header
                  div(
                    slSlot := "header",
                    styleAttr := "display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0.5rem;",
                    div(
                      styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
                      span(styleAttr := "font-weight: 600; font-size: 1rem;", meal.description),
                      span(
                        styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-500);",
                        s"• ${DateUtils.formatTime(meal.loggedAt)}"
                      )
                    ),
                    slBadge(
                      slVariant := "primary",
                      slPill    := true,
                      s"${meal.totalCalories} kcal"
                    )
                  ),

                  // Meal Items Breakdown
                  div(
                    styleAttr := "display: flex; flex-direction: column; gap: 0.75rem;",
                    div(
                      styleAttr := "display: flex; flex-wrap: wrap; gap: 0.4rem;",
                      meal.items.map { it =>
                        slTag(
                          slVariant := "neutral",
                          slSize    := "small",
                          s"${it.itemName}: ${it.estimatedCalories} kcal"
                        )
                      }
                    ),
                    if meal.aiExplanation.nonEmpty then
                      div(
                        styleAttr := "font-size: 0.85rem; color: var(--sl-color-neutral-600); font-style: italic; background: var(--sl-color-neutral-50); padding: 0.5rem 0.75rem; border-radius: var(--sl-border-radius-medium); border-left: 3px solid var(--sl-color-primary-400);",
                        meal.aiExplanation
                      )
                    else emptyNode
                  ),

                  // Meal Card Footer (Delete action)
                  div(
                    slSlot    := "footer",
                    styleAttr := "display: flex; justify-content: flex-end;",
                    slButton(
                      slSize    := "small",
                      slVariant := "danger",
                      slOutline := true,
                      slIcon(slName := "trash", slSlot := "prefix"),
                      "Delete",
                      onClick --> { _ =>
                        ApiClient.deleteMeal(meal.id).onComplete {
                          case scala.util.Success(_) =>
                            AppState.notify("Meal deleted", "neutral")
                            AppState.loadDailyData()
                          case scala.util.Failure(err) =>
                            AppState.notify(s"Failed to delete meal: ${err.getMessage}", "danger")
                        }
                      }
                    )
                  )
                )
              }
            )
        }
      ),

      // Edit Target Dialog Modal
      slDialog(
        slOpen <-- targetModalOpen.signal,
        slLabel   := "Set Daily Calorie Target",
        styleAttr := "--width: 400px;",
        div(
          styleAttr := "display: flex; flex-direction: column; gap: 1rem;",
          p(
            styleAttr := "font-size: 0.9rem; color: var(--sl-color-neutral-600); margin: 0;",
            "Enter your target calorie goal for this date:"
          ),
          input(
            tpe         := "number",
            placeholder := "2000",
            styleAttr := "box-sizing: border-box; width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--sl-color-neutral-300); border-radius: var(--sl-border-radius-medium); font-size: 1rem;",
            controlled(
              value <-- targetInput.signal,
              onInput.mapToValue --> targetInput.writer
            )
          )
        ),
        div(
          slSlot    := "footer",
          styleAttr := "display: flex; justify-content: flex-end; gap: 0.5rem;",
          slButton(
            slVariant := "neutral",
            "Cancel",
            onClick --> (_ => targetModalOpen.set(false))
          ),
          slButton(
            slVariant := "primary",
            "Save Target",
            onClick --> (_ => saveTarget())
          )
        )
      )
    )
