package ru.trett.calcul

import munit.FunSuite
import java.util.UUID
import ru.trett.calcul.model.UserSummary

class WarningBannerSuite extends FunSuite:

  test("Warning banner condition responds to currentUser key status") {
    MockDom.install()

    // 1. Unauthenticated -> No warning banner
    AppState.currentUser.set(None)
    val shouldWarnUnauth = AppState.currentUser.now().exists(!_.hasGeminiKey)
    assertEquals(shouldWarnUnauth, false)

    // 2. Authenticated WITHOUT Gemini key -> Show warning banner
    val userWithoutKey = UserSummary(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      email = "user@example.com",
      name = "User One",
      pictureUrl = None,
      hasGeminiKey = false,
      maskedGeminiKey = None
    )
    AppState.currentUser.set(Some(userWithoutKey))
    val shouldWarnNoKey = AppState.currentUser.now().exists(!_.hasGeminiKey)
    assertEquals(shouldWarnNoKey, true)

    // 3. Authenticated WITH Gemini key -> No warning banner
    val userWithKey = userWithoutKey.copy(hasGeminiKey = true, maskedGeminiKey = Some("••••••••••••1234"))
    AppState.currentUser.set(Some(userWithKey))
    val shouldWarnWithKey = AppState.currentUser.now().exists(!_.hasGeminiKey)
    assertEquals(shouldWarnWithKey, false)

    // 4. isSettingsOpen state is available
    assert(Option(AppState.isSettingsOpen).isDefined, "isSettingsOpen state must be defined")
    assertEquals(AppState.isSettingsOpen.now(), false)
    AppState.isSettingsOpen.set(true)
    assertEquals(AppState.isSettingsOpen.now(), true)
  }
