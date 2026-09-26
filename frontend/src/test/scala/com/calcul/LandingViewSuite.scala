package com.calcul

import munit.FunSuite

class LandingViewSuite extends FunSuite:

  test("LandingView renders branding, features, and sign in call to action") {
    MockDom.install()
    val element = LandingView()
    assert(Option(element).isDefined, "LandingView should be created")
    assert(Option(element.ref).isDefined, "LandingView ref should be non-null")
    assertEquals(element.ref.tagName, "DIV")
    assertEquals(element.ref.getAttribute("class"), "landing-container")
  }
