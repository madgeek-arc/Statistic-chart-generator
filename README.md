# Statistic-chart-generator
A tool for the generation of various statistic charts.

  
## Deployment

### Prerequisites

- Docker and Docker Compose
- The application WAR built via `mvn clean package`

### Quick Start

1. **Create a `config/` directory** next to `docker-compose.yml` and place your `application.yml` inside it. This file is bind-mounted read-only into the container and must contain at minimum your datasource configuration. Example:

   ```yaml
   spring:
     datasources:
       - id: monitor.public
         url: jdbc:postgresql://your-postgres-host:5432/monitor
         driver-class-name: org.postgresql.Driver
         username: dnet
         password: dnetPwd
       - id: monitor_impala.public
         url: jdbc:hive2://your-impala-host:21050/monitor;auth=noSasl
         driver-class-name: org.apache.hive.jdbc.HiveDriver
         username:
         password:
       - id: cache
         url: jdbc:hsqldb:file:/tmp/cache
         driver-class-name: org.hsqldb.jdbcDriver
         username: sa
         password:
   ```

2. **Start the stack:**

   ```bash
   docker compose up -d
   ```

   The app will be available at `http://localhost:8080`.

### Volumes

| Volume / Mount | Purpose |
|---|---|
| `./config` (bind, read-only) | External configuration (`application.yml`, `logback.xml`) |
| `logs` (named) | Application log files written by the app |
| `hsqldb-cache` (named) | Persistent HSQLDB cache database (`/tmp/cache`) |

### Building the image locally

```bash
mvn clean package
docker build -t statistic-chart-generator .
```

---

## Admin Tool
[Statistic-chart-generator-Admin](https://github.com/AthanSoulis/Statistic-chart-generator-Admin)

The tool responsible of giving the specifications for the generated statistic chart..


## Impala Compatibility Notes

### JDBC Driver

Impala is accessed via the **Apache Hive JDBC driver** (`org.apache.hive.jdbc.HiveDriver`), connecting over the HiveServer2 Thrift protocol. The Simba/Cloudera JDBC driver was evaluated but dropped due to incompatibility with CTEs and `?` parameter binding.

The driver is bundled as `hive-jdbc-4.0.1-standalone` (shaded into the DBAccess JAR). No separate driver installation is needed.

Connection URL format:
```
jdbc:hive2://<host>:<port>/<database>;auth=noSasl
```

- **Port**: typically `21050` (Impala HiveServer2)
- **`auth=noSasl`**: required to skip Kerberos negotiation when connecting without authentication

Example datasource configuration in `application.yml`:
```yaml
spring:
  datasources:
    - id: monitor_impala.public
      url: jdbc:hive2://your-impala-host:21050/monitor;auth=noSasl
      driver-class-name: org.apache.hive.jdbc.HiveDriver
      username:
      password:
```

### SQL Generation

This project’s SQL generator (module `DBAccess`) has been updated to be compatible with Apache Impala. Impala does not allow scalar subqueries in the `SELECT` list (and also not in `GROUP BY` or `ORDER BY`).

To comply with these restrictions, the SQL builder now:
- Avoids correlated scalar subqueries in `SELECT` for non-root fields.
- Builds per-related-path derived subqueries (or CTEs) that pre-aggregate by the join key.
- LEFT JOINs those derived subqueries to the root table and selects plain columns.

Benefits:
- Impala-compliant query blocks for projections, grouping, and ordering.
- Predictable performance with pre-aggregations instead of many scalar subqueries.

Implementation details:
- See `DBAccess/src/main/java/gr/uoa/di/madgik/statstool/mapping/SqlQueryTree.java` method `makeQuery(...)`.
- Non-root projections are grouped by their child-side join key and joined to the root as derived tables (aliases like `d1`, `d2`, ...).
- Inside each derived subquery, table aliases are deterministic numeric identifiers (`t1`, `t2`, `t3`, ...) along the join path. This avoids collisions (e.g., reusing the same short alias for multiple tables) and prevents accidentally using SQL/Impala reserved words as aliases (e.g., `to`).
- Root-level filters remain in the outer `WHERE`. Filters on related tables are handled via `EXISTS` correlated subqueries or placed inside derived subqueries when applicable.

Caveats:
- `COUNT(DISTINCT ...)` is kept within a single query block per derived table; multiple distinct counts may be split across multiple derived subqueries.
- Non-aggregated non-root fields are scalarized with `MIN(...)` in the derived subqueries to guarantee a single row per root key.
- When the outer query contains aggregates and also selects non-root, non-aggregated fields, those derived columns are emitted plainly and included in the outer `GROUP BY`. Any derived aggregated columns in that case are wrapped with `SUM(...)` at the outer level to remain aggregate-safe across joins.

Testing:
- See `DBAccess/src/test/java/gr/uoa/di/madgik/statstool/mapping/SqlQueryTreeTest.java` for Impala-oriented assertions verifying LEFT JOIN to derived subqueries instead of scalar subqueries, and aliasing behavior (`t1/t2/t3`) for multi-hop joins.
