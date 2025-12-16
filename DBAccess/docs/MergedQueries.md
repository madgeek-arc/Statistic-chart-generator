# Merged query examples (CTEs + FULL OUTER JOIN + UNPIVOT via UNION ALL)

This document shows concrete examples of how StatsServiceImpl merges multiple incoming queries that target the same DB profile into a single SQL statement executed once.

Key points
- Each subquery becomes a CTE (q1..qn) normalized to (y, x) columns.
- We FULL OUTER JOIN the CTEs on x using COALESCE to align disparate x values across subqueries.
- We then unpivot y1..yn back to a 2-column stream (y, x) using UNION ALL so downstream formatters still receive [y,x] rows.
- An outer ORDER BY is applied (defaults to x when not provided).
- A final LIMIT is applied using the minimum positive limit among the subqueries, if any.
- Parameters from all subqueries are concatenated in-order and bound once to the merged statement.
- The profile used is <profile>.public (as in the current implementation).

Example 1: Two description-based queries aligned on x, with ORDER BY and LIMIT
- Input queries (pseudocode produced by Mapper.map with orderBy = null during merge):
  1. SELECT ? AS y, ? AS x FROM sales WHERE year >= ? ORDER BY x LIMIT 10
     params: [100, 'North', 2024]
  2. SELECT ? AS y, ? AS x FROM sales WHERE year >= ? ORDER BY x LIMIT 10
     params: [150, 'South', 2024]

- Merged SQL sent to the DB (outer ORDER BY x and LIMIT 10):

  WITH q1(y, x) AS (SELECT ? AS y, ? AS x FROM sales WHERE year >= ? ORDER BY x LIMIT 10),
       q2(y, x) AS (SELECT ? AS y, ? AS x FROM sales WHERE year >= ? ORDER BY x LIMIT 10),
       t AS (
         SELECT COALESCE(q1.x, q2.x) AS x, q1.y AS y1, q2.y AS y2
         FROM q1 FULL OUTER JOIN q2 ON q2.x = COALESCE(q1.x)
       )
  SELECT y1 AS y, x FROM t
  UNION ALL SELECT y2 AS y, x FROM t
  ORDER BY x
  LIMIT 10

- Bound parameters (in order):
  [100, 'North', 2024, 150, 'South', 2024]

- Profile: p.public

Example 2: Three subqueries

  WITH q1(y, x) AS (...),
       q2(y, x) AS (...),
       q3(y, x) AS (...),
       t AS (
         SELECT COALESCE(q1.x, q2.x, q3.x) AS x,
                q1.y AS y1, q2.y AS y2, q3.y AS y3
         FROM q1
         FULL OUTER JOIN q2 ON q2.x = COALESCE(q1.x)
         FULL OUTER JOIN q3 ON q3.x = COALESCE(q1.x, q2.x)
       )
  SELECT y1 AS y, x FROM t
  UNION ALL SELECT y2 AS y, x FROM t
  UNION ALL SELECT y3 AS y, x FROM t
  ORDER BY x

Behavioral details
- If queries target different profiles, merging is skipped and each query runs individually.
- If all subqueries allow caching, a single cache key is created from the merged SQL + parameters + profile; cache hit skips DB.
- If at least one subquery disables cache, the merged SQL executes without caching.
