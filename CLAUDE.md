# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules (skip tests)
mvn clean package -DskipTests

# Build with tests
mvn clean package

# Run a single test class
mvn test -Dtest=SqlQueryTreeTest

# Run with Docker Compose (port 8090)
docker compose up -d
```

The application runs on port **8080** (bare JAR) or **8090** (Docker Compose). The WAR artifact is at `Application/target/Statistic-chart-generator-Application-0.0.2-SNAPSHOT.war`.

For local runs outside Docker, place config at `config/application.yml` and pass `--spring.config.location=file:/path/to/config/`.

## Module Architecture

Three-module Maven project with a strict dependency chain:

```
Application (WAR, Spring Boot entry point)
  └── ChartDataFormatter (JAR, REST controllers + chart formatters)
        └── DBAccess (JAR, DB connectivity, SQL generation, caching)
```

**Application** — Spring Boot bootstrap (`appBoot.java`), component-scans both sibling modules.

**ChartDataFormatter** — REST controllers (`ChartDataFormatterRestController`, `RawDataFormatterRestController`, `TableDataFormatterRestController`) and output formatters for HighCharts, GoogleCharts, and eCharts. Also holds the `Mapper` class that translates chart requests into SQL via the `Mapping` model.

**DBAccess** — Core SQL generation (`SqlQueryBuilder` uses Groovy scripting; `SqlQueryTree` builds CTEs / derived subqueries / joins), multi-datasource support (PostgreSQL, Impala, HSQLDB cache), result caching (Redis optional, HSQLDB file-based persistent), and schema/cache REST endpoints.

## Key Design Patterns

**Impala SQL generation** avoids scalar subqueries in SELECT (Impala incompatibility). `SqlQueryTree` rewrites them as derived subqueries with LEFT JOINs, and uses numeric aliases (`t1`, `t2`, …) to avoid SQL reserved word collisions. CTEs (WITH clauses) are used for complex queries. See `DBAccess/docs/MergedQueries.md` for details.

Known Impala constraints enforced in `SqlQueryTree`:
- OR filter groups on the root table emit `(col=? OR col=?)` — not EXISTS+UNION ALL (Impala cannot resolve correlated column references inside UNION ALL subqueries)
- `=` / `!=` filters with multiple values generate `IN` / `NOT IN`
- ORDER BY uses the aggregate expression by name, not a positional `1`

**Multi-query merging** combines multiple queries into a single SQL statement via CTEs + FULL OUTER JOIN to reduce round trips. See `ChartDataFormatter/docs/MultiQueryFormatting.md`.

**Hive JDBC driver** — The project uses the Apache Hive JDBC 4.0.1 standalone driver (not Simba/Cloudera). The Maven Shade plugin strips its bundled SLF4J to avoid classpath conflicts.

## Database & Datasource Config

Configured in `DBAccess/src/main/resources/application.yml` (template) and overridden by the external `config/application.yml` at runtime:
- Multi-datasource: PostgreSQL, Impala, HSQLDB cache
- HikariCP pool: max 55 connections
- Default result limit: 70 rows

## Endpoints

- `GET /health/readiness`, `GET /health/liveness` — health probes
- `GET /prometheus` — Prometheus metrics
- Chart/data endpoints on `ChartDataFormatterRestController`
- Schema and cache management on `SchemaController`, `CacheController`

Sample JSON request payloads are in `Application/src/main/resources/public/jsonFiles/`.

## CI/CD

**Jenkinsfile** — builds, tags, and pushes to `docker-registry.openaire.eu/stats-tool/statistic-chart-generator`. The Deploy stage is currently disabled (Kubernetes apply with image-tag substitution). Credentials IDs: `openaire-docker-registry-lempesis`, `github-creds`.

**GitHub Actions** (`.github/workflows/maven.yml`) — lightweight Maven build on push/PR to master (currently uses Java 1.8 and needs updating to Java 17).
