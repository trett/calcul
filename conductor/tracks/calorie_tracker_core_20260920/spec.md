# Specification: Build AI Calorie & Weight Tracker Core Application

## 1. Objective & Scope
Build the foundational end-to-end implementation of the AI Calorie & Weight Tracker as a single, self-contained application:
- Scala 3 (Scala 3.9.0) direct-style backend using SoftwareMill Ox and Tapir.
- Frontend Single Page Application (SPA) using Scala.js, Laminar, and `laminar-shoelace-components`.
- Multimodal meal analysis (photo + text) using Gemini Flash models via `sttp-ai`.
- Daily calorie target tracking, daily calorie aggregation, and daily weight tracking.
- Google OAuth2 authentication flow.
- PostgreSQL database persistence initialized via a canonical `schema.sql`.
- Reflection-free architecture ready for GraalVM Native Image compilation and Docker deployment.

## 2. Architecture & Modules
The project is organized as an sbt multi-module build inspired by Bootzooka:
- **`shared`**: Cross-compiled (JVM + Scala.js). Contains core domain case classes, DTOs, JSON codecs (compile-time macro derivation), and Tapir endpoint declarations.
- **`backend`**: Scala 3 (JVM 21+). Implements Tapir endpoints with Netty direct-style/Ox, database access layer, Gemini Flash AI service, Google OAuth2, and static file serving for the compiled frontend SPA.
- **`frontend`**: Scala.js with Laminar and Shoelace. Single page application providing the responsive user interface.

## 3. Data Model & Database Schema (`schema.sql`)
Zero-reflection schema initialization via `schema.sql`:
- **`users`**: `id` (UUID), `google_id` (VARCHAR UNIQUE), `email` (VARCHAR), `name` (VARCHAR), `picture_url` (VARCHAR), `created_at` (TIMESTAMPTZ).
- **`daily_targets`**: `user_id` (UUID FK), `target_date` (DATE), `calorie_target` (INTEGER), PRIMARY KEY (`user_id`, `target_date`).
- **`meals`**: `id` (UUID PRIMARY KEY), `user_id` (UUID FK), `logged_at` (TIMESTAMPTZ), `meal_date` (DATE), `description` (TEXT), `image_path` (VARCHAR nullable), `total_calories` (INTEGER), `ai_explanation` (TEXT).
- **`meal_items`**: `id` (UUID PRIMARY KEY), `meal_id` (UUID FK), `item_name` (VARCHAR), `estimated_calories` (INTEGER).
- **`daily_weights`**: `user_id` (UUID FK), `weigh_date` (DATE), `weight` (NUMERIC(5,2)), `unit` (VARCHAR(10)), PRIMARY KEY (`user_id`, `weigh_date`).

## 4. API Endpoints (Tapir Declarations in `shared`)
- **Authentication:**
  - `GET /api/auth/login`: Redirects to Google OAuth2 consent page.
  - `GET /api/auth/callback`: Exchanges auth code for Google user profile, creates session, sets HTTP-only secure cookie.
  - `GET /api/auth/me`: Returns currently authenticated user details.
  - `POST /api/auth/logout`: Clears session cookie.
- **AI Analysis & Meals:**
  - `POST /api/meals/analyze`: Accepts multipart image and/or text description, calls Gemini Flash via `sttp-ai`, returns recognized items, estimated calories, and rationale.
  - `POST /api/meals`: Persists confirmed meal entry (with items, calories, and timestamp).
  - `GET /api/meals?date=YYYY-MM-DD`: Returns chronological list of meals for the selected date.
  - `DELETE /api/meals/:id`: Deletes a meal entry.
- **Calorie Targets & Aggregations:**
  - `GET /api/calories/daily?date=YYYY-MM-DD`: Returns target calories, consumed calories, and remaining balance for date.
  - `PUT /api/calories/target`: Sets or updates daily calorie target.
- **Weight Tracking:**
  - `POST /api/weights`: Records daily weigh-in (date, weight, unit).
  - `GET /api/weights?from=YYYY-MM-DD&to=YYYY-MM-DD`: Returns weight history for date range.
- **Static Assets:**
  - `GET /`: Serves `index.html`.
  - `GET /assets/*`: Serves compiled Scala.js bundle and Shoelace static assets.

## 5. Gemini Flash AI Integration (`sttp-ai`)
- Uses direct-style HTTP requests with SoftwareMill Ox and `sttp-ai`.
- Prompt instructs Gemini Flash to evaluate the meal image and/or description:
  - Strict JSON output format: `{"items": [{"name": string, "calories": int}], "total_calories": int, "explanation": string}`.
  - Compile-time macro derivation for response parsing.

## 6. Frontend UI Components (Laminar + Shoelace)
- **Header / Navigation:** User profile avatar, date navigation controls, theme switcher.
- **Quick Ingestion Widget:** Input text or attach food photo; triggers AI analysis; displays editable confirmation modal.
- **Day Dashboard:** Calorie budget dial/progress bar (consumed vs. target), remaining calorie counter.
- **Timeline Feed:** Chronological cards for each meal logged today with item breakdown and AI reasoning.
- **Weigh-in Card:** Quick daily weight input and trend display.
- **Calendar & History View:** Interactive date picker to view past days.

## 7. GraalVM Native Image & Deployment
- Strict reflection-free implementation (no Jackson, no Flyway).
- Direct-style Ox / virtual threads compatible with GraalVM Native Image on Java 21+.
- Multi-stage Dockerfile:
  - Stage 1: Build Scala.js frontend and GraalVM native binary.
  - Stage 2: Minimal distroless/alpine runtime containing the standalone native binary and static assets.
- `docker-compose.yml` for local dev with PostgreSQL and automatic `schema.sql` mounting.
