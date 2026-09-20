# Technology Stack: AI Calorie Tracker

## 1. Core Language & Concurrency Model
- **Language:** Scala 3 (Scala 3.9.0) on Java 21+.
- **Concurrency & Programming Model:** Direct-style Scala using **SoftwareMill Ox** (structured concurrency leveraging JVM virtual threads / Project Loom). Clean, blocking-syntax direct style without monads or async `Future`.
- **JSON Serialization (Reflection-free):** Compile-time macro derivation (e.g., `circe-derivation` or `upickle` in Scala 3) — zero runtime reflection, strictly GraalVM native-image friendly.
- **Build Tool:** sbt with a multi-module structure:
  - `shared`: Common domain models, DTOs, and shared Tapir endpoint definitions.
  - `backend`: Server application, DB access, Gemini API integration via Ox, and static asset handler.
  - `frontend`: Scala.js SPA compiled to static web assets.

## 2. Backend & Architecture (Direct-Style Bootzooka)
- **Base Architecture:** Bootzooka modular pattern adapted for direct-style Scala 3.
- **HTTP & API Layer:** Tapir with Netty direct-style/Ox server backend for type-safe, OpenAPI-documented endpoints.
- **AI Integration:** `sttp-ai` (SoftwareMill) calling Google Gemini Flash models using direct-style HTTP requests for multimodal (photo + prompt) calorie analysis and structured JSON parsing.
- **Authentication:** Google OAuth2 flow with secure session / HTTP-only cookie management.
- **Database & Storage:** PostgreSQL.
  - **Schema Setup:** Zero-reflection approach; no Flyway. A single canonical SQL file (`schema.sql` / Docker init script) run directly to set up the schema.
  - **Data Access:** Lightweight, direct SQL via simple JDBC / Anorm (no heavy reflection ORM) running smoothly on virtual threads.
- **Configuration:** PureConfig / light Scala-based configuration without runtime reflection.

## 3. Frontend (Scala.js SPA)
- **Runtime:** Scala.js (Scala 3).
- **UI Framework:** Laminar (reactive FRP for Scala.js).
- **Component System:** `laminar-shoelace-components` leveraging Shoelace Web Components.
- **Distribution:** Compiled JavaScript and web assets are embedded into backend static resources, served from a single unified application.

## 4. Packaging, Native Compilation & Deployment
- **GraalVM Native Image:** Ahead-of-time compilation to a standalone native binary with instant startup and minimal RAM footprint. Reflection-free design guarantees smooth GraalVM native compilation without complex reflection-config JSON files.
- **Containerization:** Multi-stage Docker image packaging the native executable, ready for cloud deployment.
- **Local Development:** `docker-compose.yml` with PostgreSQL and automated `schema.sql` mounting for instant local startup.
- **Tooling & IDE Support:** Metals MCP server (`metals-mcp --workspace ./`) available for Scala code intelligence.

## 5. Testing & Quality Assurance
- **Unit & Integration Testing:** MUnit with direct-style assertions.
- **Integration Tests:** Direct testing against containerized/local PostgreSQL.
