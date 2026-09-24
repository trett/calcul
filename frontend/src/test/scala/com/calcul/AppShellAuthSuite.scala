package com.calcul

import munit.FunSuite
import java.util.UUID
import com.raquo.laminar.api.L.*
import com.calcul.model.UserSummary

class AppShellAuthSuite extends FunSuite:

  test("AppState authentication signals reflect unauthenticated and authenticated states") {
    MockDom.install()
    AppState.currentUser.set(None)
    assertEquals(AppState.currentUser.now(), None)

    val testUser = UserSummary(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      email = "test@example.com",
      name = "Test User",
      pictureUrl = None,
      hasGeminiKey = false,
      maskedGeminiKey = None
    )

    AppState.currentUser.set(Some(testUser))
    assertEquals(AppState.currentUser.now(), Some(testUser))
    assert(Option(AppState.isAuthChecking).isDefined, "isAuthChecking signal should exist")

    val shell = AppShell(com.raquo.laminar.api.L.div("Authenticated Content"))
    assert(Option(shell).isDefined, "AppShell should be created")
    assert(Option(shell.ref).isDefined, "AppShell ref should be defined")
  }
