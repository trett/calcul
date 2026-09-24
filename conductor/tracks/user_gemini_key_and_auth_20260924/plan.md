# Implementation Plan: User-Provided Gemini API Key & Authentication Gating

## Phase 1: Database Schema & Secure Key Storage
- [x] Task: Database Schema Update
    - [x] Write Tests for schema initialization and user table columns in TestDbInit
    - [x] Add `encrypted_gemini_api_key TEXT` column to `schema.sql`
- [x] Task: AES-256-GCM Encryption Utility
    - [x] Write Tests for `CryptoUtilsSuite` verifying encryption, decryption, authentication tag, and invalid key handling
    - [x] Implement `CryptoUtils` using Java standard library `javax.crypto` (AES-GCM, zero-reflection)
- [x] Task: User Repository Key Operations
    - [x] Write Tests in `RepositorySuite` for updating, retrieving, and clearing user's encrypted Gemini API key
    - [x] Implement `updateGeminiKey` and `clearGeminiKey` in `UserRepository`
- [x] Task: Conductor - User Manual Verification 'Phase 1: Database Schema & Secure Key Storage' (Protocol in workflow.md)

## Phase 2: Key Validation, Gemini Service & API Endpoints
- [x] Task: Shared Domain Models & Tapir Endpoints
    - [x] Write Tests for DTO JSON serialization (`GeminiKeyStatus`, `SaveGeminiKeyRequest`) in `DomainModelsSuite`
    - [x] Add shared models and declare Tapir endpoints for `GET /api/user/settings`, `POST /api/user/settings/gemini-key`, and `DELETE /api/user/settings/gemini-key` in `Endpoints.scala`
- [x] Task: Gemini Key Validation & Per-User Gemini Service
    - [x] Write Tests in `GeminiServiceSuite` for key validation call and user-specific key execution
    - [x] Implement `validateKey` and update `GeminiService.analyzeMeal` to decrypt and use the caller's key
- [x] Task: Server Routes & Authentication Enforcement
    - [x] Write Tests in `ServerRoutesSuite` asserting 401 on unauthenticated access and successful key management
    - [x] Implement route handlers in `ServerRoutes.scala` for settings endpoints and connect to `UserRepository` & `GeminiService`
- [x] Task: Conductor - User Manual Verification 'Phase 2: Key Validation, Gemini Service & API Endpoints' (Protocol in workflow.md)

## Phase 3: Frontend Authentication Gating & Landing Screen
- [x] Task: Public Landing Screen Component
    - [x] Write Tests / component scaffolding for `LandingView`
    - [x] Implement `LandingView` with application branding, features overview, and Google Login button
- [x] Task: AppShell Authentication Gate
    - [x] Write Tests for `AppState` authentication status signals and routing
    - [x] Update `AppShell.scala` to conditionally render `LandingView` when unauthenticated and full application shell when logged in
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Frontend Authentication Gating & Landing Screen' (Protocol in workflow.md)

## Phase 4: Frontend Settings View & Gemini Key Warnings
- [ ] Task: Missing Key Warning Banner
    - [ ] Write Tests for warning banner visibility based on `AppState.currentUser` key status
    - [ ] Implement warning banner in `HeaderView` / `AppShell` with direct navigation to Settings
- [ ] Task: User Settings View & Modal
    - [ ] Write Tests for settings view state and API key validation trigger
    - [ ] Implement `SettingsView` dialog allowing users to view key status, input/test/save a new key, and remove the key
- [ ] Task: AI Meal Analysis Gating
    - [ ] Write Tests for meal analysis prevention when key is unconfigured
    - [ ] Update `MealLogView` / `DashboardView` to prompt the user to configure their key if attempting analysis without one
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Frontend Settings View & Gemini Key Warnings' (Protocol in workflow.md)

## Phase 5: End-to-End Verification & Quality Polish
- [ ] Task: System Integration Verification
    - [ ] Write and run end-to-end integration tests verifying unauthenticated gating, key save, and meal analysis with user key
    - [ ] Run complete test suite via `sbt test` (verifying all tests pass with Testcontainers PostgreSQL)
- [ ] Task: Code Quality & Formatting
    - [ ] Run `./scripts/test_code_quality_setup.sh` to auto-format and apply Scalafix rules
    - [ ] Verify zero warnings/errors under `-Werror`
- [ ] Task: Conductor - User Manual Verification 'Phase 5: End-to-End Verification & Quality Polish' (Protocol in workflow.md)
