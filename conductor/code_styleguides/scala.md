# Scala 3 Code Style Guide

Based on the [Official Scala Style Guide](https://docs.scala-lang.org/style/) and adapted for modern Scala 3 direct-style development.

## 1. Tooling & Enforcement
- **Scalafmt:** All Scala code must be formatted using `.scalafmt.conf` configured with the most strict formatting rules (e.g., `maxColumn = 120`, `align.preset = more`, rewrite rules for imports, trailing commas, etc.).
- **Scalafix:** Scalafix must be enabled with the most strict linting and rewrite rules (e.g., `DisableSyntax`, `RemoveUnused`, `LeakingImplicitClassVal`, `ExplicitResultTypes`).
- **Compiler Flags:** Strict compiler options enabled in `build.sbt`:
  - `-Werror` (warnings as errors)
  - `-Wunused:all`
  - `-deprecation`, `-feature`, `-unchecked`

## 2. Scala 3 Syntax & Direct Style Conventions
- **Syntax:** Use clean Scala 3 significant indentation syntax (omit curly braces where clean and idiomatic).
- **Concurrency & Flow:**
  - Prefer direct style with **Ox** over reactive/monadic wrappers (`Future`, `IO`, `Task`).
  - Use structured concurrency scopes (`supervised`, `par`, `race`) and virtual threads.
- **Data Modeling:**
  - Use `case class` and `enum` for ADTs (Algebraic Data Types).
  - Prefer immutable data structures exclusively.
  - Mark public methods and exported APIs with explicit return types (`ExplicitResultTypes`).
- **Error Handling:**
  - Model expected domain errors explicitly using sealed traits / `enum` or `Either[DomainError, A]`.
  - Do not use exceptions for expected control flow.
- **Reflection & Serialization:**
  - Avoid reflection, dynamic inspection, and runtime bytecode generation to maintain GraalVM Native Image compatibility.
  - Use compile-time derivation for JSON codecs (e.g. `circe-derivation` or `upickle`).

## 3. Naming Conventions & Organization
- **Packages:** `lowercase`, single-word or reverse-domain (e.g., `ru.trett.calcul...`).
- **Classes/Traits/Enums/Objects:** `PascalCase`.
- **Methods & Values:** `camelCase`.
- **Constants:** `UpperCamelCase` or `UPPER_SNAKE_CASE` only for truly static literals.
- **Imports:** Grouped and alphabetized; eliminate unused imports.
