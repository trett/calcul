package com.calcul

import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main:

  def main(args: Array[String]): Unit =
    val init = () =>
      AppState.initTheme()
      AppState.loadCurrentUser()
      AppState.loadDailyData()

      val root = dom.document.getElementById("app")
      if root != null then
        val content = div(
          child <-- AppState.activeTab.signal.map {
            case "dashboard" =>
              DashboardView()
            case "weights" =>
              div(
                cls       := "tab-content-weights",
                styleAttr := "display: flex; flex-direction: column; gap: 1.5rem;"
              )
            case other =>
              div(p(s"Unknown view: $other"))
          }
        )
        render(root, AppShell(content))

    if dom.document.readyState == "loading" then
      dom.document.addEventListener("DOMContentLoaded", (_: dom.Event) => init())
    else init()
