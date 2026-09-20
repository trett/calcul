# Implementation Plan: Build AI Calorie & Weight Tracker Core Application

## Phase 1: Project Scaffolding & Multi-Module Build Setup (Checkpoint: 59a92e8)
- [x] Task: Multi-Module sbt Configuration (b7f1243)
    - [x] Write configuration verification tests for sbt build structure
    - [x] Implement `build.sbt`, `project/plugins.sbt`, and `project/build.properties` supporting `shared`, `backend`, and `frontend` modules on Scala 3.9.0
- [x] Task: Code Quality & Tooling Setup (08685fc)
    - [x] Write lint and format test checks
    - [x] Configure `.scalafmt.conf` and `.scalafix.conf` with strictest rules and add compiler flags in `build.sbt`
- [x] Task: PostgreSQL Schema Script & Database Connectivity (f268870)
    - [x] Write integration test verifying connection to PostgreSQL and execution of `schema.sql`
    - [x] Implement `schema.sql` and direct JDBC connection pool configuration
- [x] Task: Conductor - User Manual Verification 'Phase 1: Project Scaffolding & Multi-Module Build Setup' (Protocol in workflow.md)

## Phase 2: Domain Modeling, Shared Endpoints & Direct-Style Backend (Checkpoint: 9853af1)
- [x] Task: Shared Domain Models & JSON Codecs (c5feb5c)
    - [x] Write unit tests for domain case classes and reflection-free compile-time macro JSON serialization
    - [x] Implement shared domain models, DTOs, and compile-time codecs in `shared` module
- [x] Task: Shared Tapir API Endpoint Declarations (151fbde)
    - [x] Write unit tests verifying Tapir endpoint definitions and schema generation
    - [x] Implement Tapir endpoint declarations in `shared` module for Auth, Meals, AI Analysis, Calories, and Weight
- [x] Task: Direct-Style Netty Server & Database Repositories (0a15e92)
    - [x] Write repository unit and integration tests for users, meals, calorie targets, and daily weights
    - [x] Implement direct-style JDBC repositories and Netty/Ox HTTP server routes in `backend` module
- [x] Task: Conductor - User Manual Verification 'Phase 2: Domain Modeling, Shared Endpoints & Direct-Style Backend' (Protocol in workflow.md)

## Phase 3: AI Service (sttp-ai + Gemini Flash) & Multimodal Meal Ingestion (Checkpoint: 023d0d4)
- [x] Task: Gemini Flash Client via sttp-ai (141a2df)
    - [x] Write unit tests mocking Gemini Flash response parsing for food items, calorie estimation, and explanation
    - [x] Implement direct-style `GeminiService` using `sttp-ai` with structured JSON output parsing
- [x] Task: Meal Logging & Analysis Endpoints (2cce618)
    - [x] Write integration tests for `POST /api/meals/analyze` and `POST /api/meals`
    - [x] Implement backend endpoint logic for multimodal image upload, description analysis, and meal persistence
- [x] Task: Conductor - User Manual Verification 'Phase 3: AI Service (sttp-ai + Gemini Flash) & Multimodal Meal Ingestion' (Protocol in workflow.md)

## Phase 4: Calorie Target, Weight Tracking & Daily Aggregations (Checkpoint: d78f77f)
- [x] Task: Daily Calorie Budget & Aggregation Service (415de93)
    - [x] Write unit tests for daily calorie summation, budget calculation, and historical date queries
    - [x] Implement backend service and endpoint handlers for `GET /api/calories/daily` and `PUT /api/calories/target`
- [x] Task: Daily Weight Tracking Service (b9a20f1)
    - [x] Write unit tests for daily weigh-in recording and date-range history retrieval
    - [x] Implement backend service and endpoint handlers for `POST /api/weights` and `GET /api/weights`
- [x] Task: Conductor - User Manual Verification 'Phase 4: Calorie Target, Weight Tracking & Daily Aggregations' (Protocol in workflow.md)

## Phase 5: Google OAuth2 Authentication (Checkpoint: 464bf71)
- [x] Task: Google OAuth2 Flow & Session Management (aeda343)
    - [x] Write unit tests for OAuth token exchange, Google profile extraction, and secure session cookie verification
    - [x] Implement OAuth2 endpoints (`/api/auth/login`, `/api/auth/callback`, `/api/auth/me`, `/api/auth/logout`) and auth middleware
- [x] Task: Conductor - User Manual Verification 'Phase 5: Google OAuth2 Authentication' (Protocol in workflow.md)

## Phase 6: Scala.js Frontend with Laminar & Shoelace Components
- [ ] Task: Frontend Application Shell & Shoelace Theme Integration
    - [ ] Write frontend component tests for navigation shell and theme state
    - [ ] Implement Laminar root component, router, and `laminar-shoelace-components` setup
- [ ] Task: Meal Ingestion & AI Review UI Component
    - [ ] Write frontend tests for meal input form and AI review confirmation dialog
    - [ ] Implement photo upload / text description inputs, loading indicator, and editable calorie confirmation modal
- [ ] Task: Daily Dashboard, Timeline Feed & Calendar View
    - [ ] Write frontend tests for daily progress bar, meal timeline list, and calendar navigation
    - [ ] Implement daily calorie dial/progress component, chronological meal timeline, and date picker
- [ ] Task: Daily Weight Logging Component
    - [ ] Write frontend tests for weigh-in form and weight history display
    - [ ] Implement weigh-in entry card and weight trend visualization
- [ ] Task: Conductor - User Manual Verification 'Phase 6: Scala.js Frontend with Laminar & Shoelace Components' (Protocol in workflow.md)

## Phase 7: GraalVM Native Image Compilation & Docker Packaging
- [ ] Task: Static Asset Serving & GraalVM Native Image Setup
    - [ ] Write verification tests for embedded static asset serving and reflection-free native build configuration
    - [ ] Implement backend static resource serving and `sbt-native-packager` GraalVM native image settings
- [ ] Task: Containerization & Local Dev Orchestration
    - [ ] Write verification tests for Docker image build and healthcheck endpoints
    - [ ] Implement multi-stage Dockerfile and `docker-compose.yml` with PostgreSQL and `schema.sql` mounting
- [ ] Task: Conductor - User Manual Verification 'Phase 7: GraalVM Native Image Compilation & Docker Packaging' (Protocol in workflow.md)
