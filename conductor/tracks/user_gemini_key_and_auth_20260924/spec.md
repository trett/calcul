# Specification: User-Provided Gemini API Key & Authentication Gating

## Overview
This feature introduces per-user Gemini API key management and strictly enforces authentication across the application. Previously, the application relied on a shared server-side `GEMINI_API_KEY` and allowed unauthenticated viewing of application views. With this feature, all core application functionality (dashboard, meals, weigh-ins, settings) is gated behind Google OAuth authentication. Each user must provide their own Gemini API key in User Settings, which is validated against the Gemini API and encrypted at rest in PostgreSQL.

## Functional Requirements
1. **Authentication Gating & Landing Screen:**
   - Unauthenticated visitors accessing the root URL (`/`) see a clean landing page with app branding, value proposition, and a prominent "Sign in with Google" button.
   - The main application shell (navigation tabs, dashboard, meal logging, weight tracking, settings) is strictly hidden and inaccessible until a valid user session is established.
   - All backend data and mutation endpoints (meals, daily targets, weights, AI analysis, user settings) reject unauthenticated requests with HTTP 401 Unauthorized.

2. **User Settings & Gemini API Key Management:**
   - Add a Settings view/dialog where users can manage their account and Gemini API key.
   - Display key status: indicating whether a key is configured (showing a masked preview `••••••••••••abcd`) or missing.
   - Provide input field for entering or updating the Gemini API key.
   - Provide a "Validate & Save" action:
     - Backend makes a lightweight test call to Google Gemini Flash API (`generateContent` with a minimal prompt) using the submitted key.
     - If validation fails, return a clear error message (e.g. "Invalid Gemini API key") and do not save.
     - If validation succeeds, encrypt the key and save it to PostgreSQL.
   - Provide an option to delete / remove the stored API key.

3. **Database & Encryption at Rest:**
   - Add `encrypted_gemini_api_key TEXT` to the `users` table schema in `schema.sql`.
   - Backend encrypts the API key before persisting to PostgreSQL using AES-GCM (256-bit) with a server secret (`SESSION_SECRET`).
   - Plaintext API keys are never stored in the database and never sent back to the frontend (only masked preview or boolean presence flag).

4. **Missing Key Prompts & AI Analysis Enforcement:**
   - When a logged-in user does not have a Gemini API key configured:
     - Display a prominent warning banner at the top of the interface: "Gemini API key required. Please configure your key in User Settings to enable AI meal analysis."
     - Disable the AI meal analysis button or intercept it with a prompt directing the user to Settings.
   - During meal analysis (`POST /api/meals/analyze`):
     - Backend retrieves and decrypts the user's saved Gemini API key.
     - If no key exists, return HTTP 400 Bad Request with a clear message requesting API key configuration.
     - Use the user's decrypted key for the Gemini Flash API request via `sttp-ai`.

## Non-Functional Requirements
- **Security:** Standard AES-256-GCM authenticated encryption; zero plaintext leakage in logs or client-facing responses.
- **GraalVM Native Image Compatibility:** Encryption implemented using standard Java standard library cryptography (`javax.crypto.Cipher`, `java.security.SecureRandom`) without external reflection-heavy libraries.
- **UX Consistency:** Seamless integration into Shoelace-based UI components (alerts, dialogs, buttons) and Laminar reactive state.

## Acceptance Criteria
- [ ] An unauthenticated user sees only the public landing page with Google Login; no dashboard or tracker data is rendered.
- [ ] After logging in via Google OAuth, the user is redirected into the authenticated application shell.
- [ ] If the user has not configured a Gemini API key, a persistent banner appears in the app prompting for configuration.
- [ ] Users can enter their Gemini API key in Settings; clicking "Save" validates the key against Google Gemini API before saving.
- [ ] An invalid key returns a descriptive error and is not saved.
- [ ] A valid key is saved encrypted in PostgreSQL, and the UI updates to show the key as configured (masked).
- [ ] AI meal analysis uses the authenticated user's decrypted key and succeeds.
- [ ] Users can update or delete their saved key.
- [ ] All unit, integration, and UI tests pass, and code adheres to formatting/Scalafix rules.
