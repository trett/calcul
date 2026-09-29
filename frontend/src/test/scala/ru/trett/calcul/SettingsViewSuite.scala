package ru.trett.calcul

import munit.FunSuite
import ru.trett.calcul.model.UserSummary

import java.util.UUID

class SettingsViewSuite extends FunSuite:

  test("SettingsView initializes dialog element") {
    MockDom.install()
    val dialog = SettingsView()
    assert(Option(dialog).isDefined, "SettingsView dialog should be created")
    assert(Option(dialog.ref).isDefined, "SettingsView ref should be non-null")
    assertEquals(dialog.ref.tagName, "SL-DIALOG")
  }

  test("SettingsView reacts to user key state and settings open state") {
    MockDom.install()
    val userWithoutKey = UserSummary(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      email = "user@example.com",
      name = "User One",
      pictureUrl = None,
      hasGeminiKey = false,
      maskedGeminiKey = None
    )
    AppState.currentUser.set(Some(userWithoutKey))
    AppState.isSettingsOpen.set(false)

    val dialog = SettingsView()
    assert(Option(dialog).isDefined)

    AppState.isSettingsOpen.set(true)
    assertEquals(AppState.isSettingsOpen.now(), true)

    val userWithKey = userWithoutKey.copy(hasGeminiKey = true, maskedGeminiKey = Some("••••••••••••5678"))
    AppState.currentUser.set(Some(userWithKey))
    assertEquals(AppState.currentUser.now().flatMap(_.maskedGeminiKey), Some("••••••••••••5678"))
  }
