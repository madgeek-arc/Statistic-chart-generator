# Server-side result merging examples

This project now supports merging multiple query results during query execution on the server side (DBAccess layer). If you prefer to do the merging directly in SQL (so that the DB returns a single result shaped as `[y, x, series]`), the following examples show common patterns.

Notes
- Expected row shape for multi-series charts: `[y, x, seriesLabel]`
  - y: numeric value (as text or number)
  - x: category or time bucket (string/timestamp cast to text)
  - seriesLabel: the name of the series to distinguish data from different queries
- If your query already returns three columns in that order, no extra transformation is needed.
- These examples use PostgreSQL syntax first, then Hive/Impala equivalents.

## 1) Merge two independent queries using UNION ALL (PostgreSQL)
Suppose you have two queries that return rows as `[y, x]`:
- Q1 (series "S1"): returns total sales by month
- Q2 (series "S2"): returns total refunds by month

You can apply a literal series label to each branch and UNION ALL:

```sql
WITH q1 AS (
  -- Your original query 1 (y, x)
  SELECT SUM(s.amount) AS y, TO_CHAR(s.sale_date, 'YYYY-MM') AS x
  FROM sales s
  WHERE s.sale_date >= :from AND s.sale_date < :to
  GROUP BY TO_CHAR(s.sale_date, 'YYYY-MM')
),
q2 AS (
  -- Your original query 2 (y, x)
  SELECT SUM(r.amount) AS y, TO_CHAR(r.refund_date, 'YYYY-MM') AS x
  FROM refunds r
  WHERE r.refund_date >= :from AND r.refund_date < :to
  GROUP BY TO_CHAR(r.refund_date, 'YYYY-MM')
)
SELECT y, x, 'S1' AS series
FROM q1
UNION ALL
SELECT y, x, 'S2' AS series
FROM q2
ORDER BY x, series;
```

This returns a single result shaped as:
- row 1: y
- row 2: x
- row 3: series label (S1 or S2)

Our formatter understands this as a double-group-by (x, series) set.

## 2) If your query already has a second group-by (series)
If your SQL already produces `[y, x, series]` (e.g., grouping by product category as the series), you can return it as-is:

```sql
SELECT
  SUM(s.amount) AS y,
  TO_CHAR(s.sale_date, 'YYYY-MM') AS x,
  s.product_category AS series
FROM sales s
WHERE s.sale_date >= :from AND s.sale_date < :to
GROUP BY TO_CHAR(s.sale_date, 'YYYY-MM'), s.product_category
ORDER BY x, series;
```

## 3) Parameterized variant with explicit casting and null safety (PostgreSQL)

```sql
WITH data AS (
  SELECT COALESCE(SUM(s.amount), 0) AS y,
         TO_CHAR(s.sale_date, 'YYYY-MM') AS x
  FROM sales s
  WHERE s.sale_date >= :from AND s.sale_date < :to
  GROUP BY TO_CHAR(s.sale_date, 'YYYY-MM')
)
SELECT y, x, :series_label::text AS series
FROM data
ORDER BY x;
```

Execute this twice with different `:series_label` values and UNION ALL the two results if you need a single SQL statement, or let the service perform the merge.

## 4) Hive/Impala equivalents
For engines that favor `string` casting and date formatting:

```sql
-- Impala/Hive example: two sources merged with literal labels
WITH q1 AS (
  SELECT SUM(s.amount) AS y,
         DATE_FORMAT(s.sale_date, 'yyyy-MM') AS x
  FROM sales s
  WHERE s.sale_date >= ${from} AND s.sale_date < ${to}
  GROUP BY DATE_FORMAT(s.sale_date, 'yyyy-MM')
),
q2 AS (
  SELECT SUM(r.amount) AS y,
         DATE_FORMAT(r.refund_date, 'yyyy-MM') AS x
  FROM refunds r
  WHERE r.refund_date >= ${from} AND r.refund_date < ${to}
  GROUP BY DATE_FORMAT(r.refund_date, 'yyyy-MM')
)
SELECT y, x, 'S1' AS series FROM q1
UNION ALL
SELECT y, x, 'S2' AS series FROM q2
ORDER BY x, series;
```

If you already have a series dimension:

```sql
SELECT SUM(s.amount) AS y,
       DATE_FORMAT(s.sale_date, 'yyyy-MM') AS x,
       s.product_category AS series
FROM sales s
WHERE s.sale_date >= ${from} AND s.sale_date < ${to}
GROUP BY DATE_FORMAT(s.sale_date, 'yyyy-MM'), s.product_category
ORDER BY x, series;
```

## 5) Optional: Pivot/aggregation of merged results
Sometimes you want a pivot table for debugging/inspection. PostgreSQL example:

```sql
WITH merged AS (
  SELECT y::numeric, x, series
  FROM (
    /* paste the UNION ALL from example #1 here */
  ) t
)
SELECT x,
       SUM(CASE WHEN series = 'S1' THEN y ELSE 0 END) AS s1,
       SUM(CASE WHEN series = 'S2' THEN y ELSE 0 END) AS s2
FROM merged
GROUP BY x
ORDER BY x;
```

## 6) How this integrates here
- The DBAccess service currently merges multiple Result objects into a single Result with rows shaped as `[y, x, series]` when you submit more than one query.
- If you return a single `[y, x, series]` result from your DB (using the patterns above), the formatters will treat it as a double-group-by input and render series correctly.
- If you return only `[y, x]`, the service will label each query with its Query.name and augment the rows to `[y, x, name]` during merging.

## 7) Ordering and nulls
- Consider `ORDER BY x, series` for consistent output.
- Use `COALESCE` (PostgreSQL) or `NVL` (Hive) to control nulls in y.
- x and series should be textual or castable to textual so the chart formatters can create category axes and series names.
