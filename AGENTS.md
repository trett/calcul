# Agent Guidelines & Repository Rules

These guidelines are mandatory for all AI agents and contributors working in this repository.

## 1. Pull Request Workflow
- All new changes, features, bug fixes, or refactorings must be developed on a dedicated feature branch and submitted via a separate Pull Request (PR).
- Never commit changes directly to the `main` branch.
- **Remote Push Constraint:** Never push changes or branches to a remote repository without explicit confirmation or request from the user.

## 2. Pre-PR Quality & Linting Verification
- Before opening any Pull Request or finalizing any changes:
  1. Run `scalafmt` across all modules:
     ```bash
     sbt scalafmtAll
     ```
  2. Run `scalafix` across all modules:
     ```bash
     sbt scalafixAll
     ```
  3. Verify that all checks pass with zero errors:
     ```bash
     ./scripts/test_code_quality_setup.sh
     ```
     (Ensures both `scalafmtCheckAll` and `scalafixAll --check` succeed cleanly).
  4. Verify that the test suite passes:
     ```bash
     sbt test
     ```
- No PR may be created with formatting diffs, Scalafix violations, compiler warnings (enforced by `-Werror`), or failing tests.
