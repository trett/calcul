# ==============================================================================
# Multi-stage Dockerfile for CalTrack AI (Calcul)
# ==============================================================================
# Stage 1: Build stage
# ==============================================================================
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.2_13_1.10.7_3.4.0 AS builder

WORKDIR /build

# Copy sbt build definition files first to optimize layer caching
COPY project/ /build/project/
COPY build.sbt /build/

# Copy application sources, assets, and SQL schema
COPY shared/ /build/shared/
COPY backend/ /build/backend/
COPY frontend/ /build/frontend/
COPY schema.sql /build/

# Compile frontend fastLinkJS and stage backend application
RUN sbt -Dsbt.supershell=false --batch "frontend/fastLinkJS" backend/stage

# ==============================================================================
# Stage 2: Runtime image (Direct-style Virtual Threads on Java 21 JRE)
# ==============================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Install curl for container healthchecking
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Run with least privilege (non-root caltrack user)
RUN groupadd -r caltrack && useradd -r -g caltrack caltrack

# Copy staged backend distribution and canonical schema
COPY --from=builder /build/backend/target/universal/stage /app/
COPY schema.sql /app/schema.sql

RUN chown -R caltrack:caltrack /app
USER caltrack

ENV PORT=8080
EXPOSE 8080

# Health check using the backend /api/health endpoint
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

ENTRYPOINT ["/app/bin/calcul-backend"]
