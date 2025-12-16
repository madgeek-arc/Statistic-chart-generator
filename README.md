# Statistic-chart-generator
A tool for the generation of various statistic charts.

  
## Admin Tool
[Statistic-chart-generator-Admin](https://github.com/AthanSoulis/Statistic-chart-generator-Admin)

The tool responsible of giving the specifications for the generated statistic chart..


## Impala Compatibility Notes

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
- Root-level filters remain in the outer `WHERE`. Filters on related tables are handled via `EXISTS` correlated subqueries or placed inside derived subqueries when applicable.

Caveats:
- `COUNT(DISTINCT ...)` is kept within a single query block per derived table; multiple distinct counts may be split across multiple derived subqueries.
- Non-aggregated non-root fields are scalarized with `MIN(...)` in the derived subqueries to guarantee a single row per root key.

Testing:
- See `DBAccess/src/test/java/gr/uoa/di/madgik/statstool/mapping/SqlQueryTreeTest.java` for Impala-oriented assertions verifying LEFT JOIN to derived subqueries instead of scalar subqueries.
