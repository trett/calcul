package ru.trett.calcul

import munit.FunSuite

class SettingsViewSuite extends FunSuite:

  test("SettingsView initializes dialog element") {
    MockDom.install()
    val dialog = SettingsView()
    assert(Option(dialog).isDefined, "SettingsView dialog should be created")
    assert(Option(dialog.ref).isDefined, "SettingsView ref should be non-null")
    assertEquals(dialog.ref.tagName, "SL-DIALOG")
  }
