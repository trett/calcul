# Initial Concept

I need calories calculator. It should accept text based description of meals and fotos. It should request gemini API flash models to calculate calories in the meal and add sum it for the day. There should be storage to track calories by day. The stack is scala 3 with sttp-ai https://github.com/softwaremill/sttp-ai You can use https://github.com/softwaremill/bootzooka as a template for the web-project. We will deploy it as the docker image. You should use scalaJS for the frontend. PostgresDB as a database. There should be a single backend + frontend app. Frontened should be served as a static resource. It should be ready for the native compilation with GraalVM. ScalaJs should be used with Laminar and https://github.com/raquo/laminar-shoelace-components

---

# Product Guide: AI Calorie Tracker

## 1. Vision & Overview
The AI Calorie Tracker is an intelligent, low-friction nutrition and body weight companion. Instead of tedious manual food database lookups, users simply upload photos of their meals or type natural language meal descriptions. Leveraging Google Gemini Flash models via `sttp-ai`, the application identifies food items, estimates caloric content, provides explanatory reasoning, and tracks daily totals against personalized calorie targets alongside daily body weight tracking.

## 2. Target Audience & Core Use Case
- **Primary Audience:** Individuals looking for fast, frictionless calorie logging and body weight monitoring to reach health, fitness, or weight management goals.
- **Core Use Case:** Snap a photo or write "2 eggs, 1 slice sourdough toast, black coffee", get instant calorie estimates and reasoning, log daily weigh-ins, and observe how calorie intake correlates with body weight changes over time.

## 3. Key Capabilities & Features
### 3.1 Meal Ingestion & Gemini Flash AI Analysis
- **Multimodal Logging:** Input meals via text descriptions, photo uploads (camera or file), or both.
- **AI Estimation:** Uses Gemini Flash models (via `sttp-ai`) to:
  - Detect and list recognized food items.
  - Calculate total estimated calories for the meal.
  - Provide a concise AI rationale/explanation for the calculation.
- **Review & Edit:** Ability to review, adjust, or confirm estimates before persisting the entry.

### 3.2 Daily Tracking & Calorie Budget
- **Daily Calorie Target:** Set and update personalized daily caloric goals.
- **Daily Summation:** Live calculation of calories consumed vs. remaining target for the day.
- **Chronological Timeline:** Visual timeline of daily meals with timestamps, photos, item breakdowns, and calorie counts.
- **Calendar & Historical Log:** Navigate past days via a calendar picker to view historical meal logs, daily totals, and performance trends.

### 3.3 Daily Body Weight Tracking
- **Daily Weigh-in Entry:** Log daily body weight (with customizable units: kg or lbs).
- **Correlation & Progress:** Track body weight progression alongside daily caloric intake over time on the calendar and history views.

### 3.4 Security & Access
- **Google OAuth2 Authentication:** Secure login using Google accounts, backed by Google Cloud OAuth credentials.
- **Per-User Isolation:** Each authenticated user has their own private meal records, calorie goals, and weight tracking history stored securely in PostgreSQL.

## 4. Delivery & Operational Model
- **Unified Web Application:** Single backend + frontend application (Scala 3 backend serving the Scala.js + Laminar frontend as static assets).
- **Native GraalVM & Docker:** Optimized for lightweight memory footprint and instant startup with GraalVM native-image inside a Docker container.
