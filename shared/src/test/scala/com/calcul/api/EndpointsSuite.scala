package com.calcul.api

import munit.FunSuite

class EndpointsSuite extends FunSuite:

  test("Endpoints object defines all expected Tapir endpoints") {
    val endpoints = Endpoints.allEndpoints
    assert(endpoints.nonEmpty, "Endpoints list should not be empty")

    val paths = endpoints.map(_.showPathTemplate())
    assert(paths.exists(_.startsWith("/api/auth/login")), "Should have login path")
    assert(paths.exists(_.startsWith("/api/auth/callback")), "Should have callback path")
    assert(paths.exists(_.startsWith("/api/auth/me")), "Should have me path")
    assert(paths.exists(_.startsWith("/api/auth/logout")), "Should have logout path")
    assert(paths.exists(_.startsWith("/api/meals/analyze")), "Should have meal analyze path")
    assert(paths.exists(_.startsWith("/api/meals")), "Should have meals collection path")
    assert(paths.exists(_.startsWith("/api/calories/daily")), "Should have daily calories path")
    assert(paths.exists(_.startsWith("/api/calories/target")), "Should have calorie target path")
    assert(paths.exists(_.startsWith("/api/weights")), "Should have weights path")
    assert(paths.exists(_.startsWith("/api/user/settings/gemini-key")), "Should have user settings gemini-key path")
  }

  test("Endpoints.allEndpoints count matches total declared endpoints") {
    assertEquals(Endpoints.allEndpoints.size, 15)
  }
