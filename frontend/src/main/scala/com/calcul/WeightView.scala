package com.calcul

import com.raquo.laminar.api.L.*
import com.raquo.airstream.state.Var
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalDate
import com.calcul.ShoelaceDSL.*
import com.calcul.model.RecordWeightRequest

object WeightView:

  def apply(): HtmlElement =
    val weighDateVar   = Var(AppState.selectedDate.now())
    val weightInputVar = Var("")
    val unitVar        = Var("kg")
    val isSavingVar    = Var(false)

    def saveWeight(): Unit =
      val raw = weightInputVar.now().trim
      if raw.isEmpty then AppState.notify("Please enter your weight", "warning")
      else
        try
          val w   = BigDecimal(raw)
          val req = RecordWeightRequest(weighDateVar.now(), w, unitVar.now())
          isSavingVar.set(true)
          ApiClient.recordWeight(req).onComplete {
            case scala.util.Success(_) =>
              isSavingVar.set(false)
              weightInputVar.set("")
              AppState.notify(s"Logged weight: $w ${unitVar.now()}", "success")
              AppState.loadWeights()
            case scala.util.Failure(err) =>
              isSavingVar.set(false)
              AppState.notify(s"Failed to save weight: ${err.getMessage}", "danger")
          }
        catch
          case _: Exception =>
            AppState.notify("Invalid number format for weight", "danger")

    div(
      cls       := "weights-container",
      styleAttr := "display: flex; flex-direction: column; gap: 1.5rem;",

      // 1. Weigh-In Entry Card
      slCard(
        styleAttr := "width: 100%; border-radius: var(--sl-border-radius-large); box-shadow: var(--sl-shadow-small);",

        // Card Header
        div(
          slSlot    := "header",
          styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
          slIcon(slName  := "speedometer2", styleAttr := "color: var(--sl-color-primary-600); font-size: 1.25rem;"),
          span(styleAttr := "font-weight: 600; font-size: 1.05rem;", "Record Daily Weight")
        ),

        // Card Body
        div(
          styleAttr := "display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end;",

          // Date input
          div(
            styleAttr := "display: flex; flex-direction: column; gap: 0.35rem;",
            label(styleAttr := "font-size: 0.85rem; font-weight: 600; color: var(--sl-color-neutral-700);", "Date"),
            input(
              tpe := "date",
              styleAttr := "padding: 0.45rem 0.75rem; border: 1px solid var(--sl-color-neutral-300); border-radius: var(--sl-border-radius-medium); font-size: 0.95rem;",
              value <-- weighDateVar.signal.map(_.toString),
              onChange.mapToValue --> { v =>
                Option(v).filter(_.nonEmpty).foreach { str =>
                  try weighDateVar.set(LocalDate.parse(str))
                  catch case _: Exception => ()
                }
              }
            )
          ),

          // Weight numeric input
          div(
            styleAttr := "display: flex; flex-direction: column; gap: 0.35rem; flex: 1; min-width: 140px;",
            label(styleAttr := "font-size: 0.85rem; font-weight: 600; color: var(--sl-color-neutral-700);", "Weight"),
            input(
              tpe         := "number",
              stepAttr    := "0.1",
              placeholder := "e.g. 75.5",
              styleAttr := "box-sizing: border-box; width: 100%; max-width: 100%; padding: 0.45rem 0.75rem; border: 1px solid var(--sl-color-neutral-300); border-radius: var(--sl-border-radius-medium); font-size: 0.95rem;",
              controlled(
                value <-- weightInputVar.signal,
                onInput.mapToValue --> weightInputVar.writer
              )
            )
          ),

          // Unit toggle
          div(
            styleAttr := "display: flex; flex-direction: column; gap: 0.35rem;",
            label(styleAttr := "font-size: 0.85rem; font-weight: 600; color: var(--sl-color-neutral-700);", "Unit"),
            slButtonGroup(
              slButton(
                slVariant <-- unitVar.signal.map(u => if u == "kg" then "primary" else "default"),
                "kg",
                onClick --> (_ => unitVar.set("kg"))
              ),
              slButton(
                slVariant <-- unitVar.signal.map(u => if u == "lbs" then "primary" else "default"),
                "lbs",
                onClick --> (_ => unitVar.set("lbs"))
              )
            )
          ),

          // Save button
          slButton(
            slVariant := "primary",
            slLoading <-- isSavingVar.signal,
            slDisabled <-- isSavingVar.signal,
            slIcon(slName := "check-lg", slSlot := "prefix"),
            "Save Weigh-in",
            onClick --> (_ => saveWeight())
          )
        )
      ),

      // 2. Weight Trend Summary Card
      slCard(
        styleAttr := "width: 100%; border-radius: var(--sl-border-radius-large); box-shadow: var(--sl-shadow-small);",

        // Card Header
        div(
          slSlot    := "header",
          styleAttr := "display: flex; align-items: center; justify-content: space-between;",
          div(
            styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
            slIcon(slName  := "graph-up", styleAttr := "color: var(--sl-color-primary-600); font-size: 1.25rem;"),
            span(styleAttr := "font-weight: 600; font-size: 1.05rem;", "30-Day Weight History & Trend")
          ),
          slBadge(
            slVariant := "neutral",
            slPill    := true,
            child.text <-- AppState.weights.signal.map(ws => s"${ws.length} entries")
          )
        ),

        // Card Body: Key Stats & List
        div(
          styleAttr := "display: flex; flex-direction: column; gap: 1.25rem;",

          // Trend Metrics Row
          div(
            styleAttr := "display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 1rem; text-align: center;",

            // Latest
            div(
              styleAttr := "background: var(--sl-color-neutral-100); padding: 0.75rem; border-radius: var(--sl-border-radius-medium);",
              div(
                styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-600); text-transform: uppercase; font-weight: 600;",
                "Latest Weight"
              ),
              div(
                styleAttr := "font-size: 1.4rem; font-weight: 700; color: var(--sl-color-neutral-900);",
                child.text <-- AppState.weights.signal.map { ws =>
                  ws.lastOption.map(w => s"${w.weight} ${w.unit}").getOrElse("—")
                }
              )
            ),

            // 30-Day Change
            div(
              styleAttr := "background: var(--sl-color-neutral-100); padding: 0.75rem; border-radius: var(--sl-border-radius-medium);",
              div(
                styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-600); text-transform: uppercase; font-weight: 600;",
                "30-Day Delta"
              ),
              div(
                styleAttr := "font-size: 1.4rem; font-weight: 700;",
                child <-- AppState.weights.signal.map { ws =>
                  if ws.length >= 2 then
                    val delta = ws.last.weight - ws.head.weight
                    val unit  = ws.last.unit
                    if delta < 0 then span(styleAttr := "color: var(--sl-color-success-600);", s"$delta $unit")
                    else if delta > 0 then span(styleAttr := "color: var(--sl-color-warning-600);", s"+$delta $unit")
                    else span(styleAttr                   := "color: var(--sl-color-neutral-600);", s"0.0 $unit")
                  else span(styleAttr := "color: var(--sl-color-neutral-500);", "—")
                }
              )
            ),

            // Min
            div(
              styleAttr := "background: var(--sl-color-neutral-100); padding: 0.75rem; border-radius: var(--sl-border-radius-medium);",
              div(
                styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-600); text-transform: uppercase; font-weight: 600;",
                "30-Day Low"
              ),
              div(
                styleAttr := "font-size: 1.4rem; font-weight: 700; color: var(--sl-color-neutral-900);",
                child.text <-- AppState.weights.signal.map { ws =>
                  if ws.nonEmpty then s"${ws.map(_.weight).min} ${ws.head.unit}" else "—"
                }
              )
            ),

            // Max
            div(
              styleAttr := "background: var(--sl-color-neutral-100); padding: 0.75rem; border-radius: var(--sl-border-radius-medium);",
              div(
                styleAttr := "font-size: 0.8rem; color: var(--sl-color-neutral-600); text-transform: uppercase; font-weight: 600;",
                "30-Day High"
              ),
              div(
                styleAttr := "font-size: 1.4rem; font-weight: 700; color: var(--sl-color-neutral-900);",
                child.text <-- AppState.weights.signal.map { ws =>
                  if ws.nonEmpty then s"${ws.map(_.weight).max} ${ws.head.unit}" else "—"
                }
              )
            )
          ),

          // History List
          div(
            styleAttr := "display: flex; flex-direction: column; gap: 0.5rem;",
            span(styleAttr := "font-weight: 600; font-size: 0.95rem;", "Recent Logs"),
            child <-- AppState.weights.signal.map { weights =>
              if weights.isEmpty then
                div(
                  styleAttr := "text-align: center; padding: 1.5rem; color: var(--sl-color-neutral-500); font-size: 0.9rem;",
                  "No weigh-in records found for this period. Use the form above to record your weight!"
                )
              else
                div(
                  styleAttr := "display: flex; flex-direction: column; gap: 0.4rem;",
                  weights.reverse.map { w =>
                    div(
                      styleAttr := "display: flex; align-items: center; justify-content: space-between; padding: 0.6rem 0.85rem; background: var(--sl-color-neutral-50); border: 1px solid var(--sl-color-neutral-200); border-radius: var(--sl-border-radius-medium);",
                      div(
                        styleAttr := "display: flex; align-items: center; gap: 0.5rem;",
                        slIcon(slName  := "calendar-event", styleAttr := "color: var(--sl-color-neutral-500);"),
                        span(styleAttr := "font-weight: 500; font-size: 0.95rem;", w.weighDate.toString)
                      ),
                      slBadge(
                        slVariant := "primary",
                        slPill    := true,
                        s"${w.weight} ${w.unit}"
                      )
                    )
                  }
                )
            }
          )
        )
      )
    )
