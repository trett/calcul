package ru.trett.calcul

import com.raquo.laminar.api.L.*
import ru.trett.calcul.ShoelaceDSL.*

object TabBarView:

  def apply(): HtmlElement =
    div(
      cls := "tab-bar",
      child.maybe <-- AppState.currentUser.signal.map {
        case Some(_) =>
          Some(
            slButtonGroup(
              slButton(
                slSize := "small",
                slVariant <-- AppState.activeTab.signal.map(t => if t == "dashboard" then "primary" else "default"),
                slIcon(slName := "calendar3"),
                "Dashboard",
                onClick --> (_ => AppState.activeTab.set("dashboard"))
              ),
              slButton(
                slSize := "small",
                slVariant <-- AppState.activeTab.signal.map(t => if t == "weights" then "primary" else "default"),
                slIcon(slName := "speedometer2"),
                "Weight History",
                onClick --> { _ =>
                  AppState.activeTab.set("weights")
                  AppState.loadWeights()
                }
              )
            )
          )
        case None => None
      }
    )
