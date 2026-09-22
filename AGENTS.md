# Agent Guidelines & Repository Rules

These guidelines are mandatory for all AI agents and contributors working in this repository.

## 1. Pull Request Workflow
- All new changes, features, bug fixes, or refactorings must be developed on a dedicated feature branch and submitted via a separate Pull Request (PR).
- Never commit changes directly to the `main` branch.
- **Remote Push Constraint:** Never push changes or branches to a remote repository without explicit confirmation or request from the user.

## 2. Pre-PR Quality & Linting Verification
- Before opening any Pull Request or finalizing any changes:
  1. Auto-format and apply Scalafix rules:
     ```bash
     ./scripts/test_code_quality_setup.sh
     ```
  2. Verify that the test suite passes:
     ```bash
     sbt test
     ```
- No PR may be created with Scalafix violations, compiler warnings (enforced by `-Werror`), or failing tests.
