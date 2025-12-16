# DataFormatter: Handling Multiple Queries

This document explains how the Chart Data Formatter layer transforms multiple DB query results into a single JSON payload suitable for chart rendering, with emphasis on HighCharts formatting.

Applies to:
- HighCharts formatter: `HighChartsDataFormatter#toJsonResponse` → `multiToHighChartsJsonResponse`
- Shared utilities: `DataFormatter#getXAxisCategories`

See also:
- DB query merging in DBAccess: `DBAccess/docs/MergedQueries.md`

---

## Overview
When the frontend requests multiple charts (or multiple series for the same chart), the formatter receives a list of `Result` objects (one per subquery) plus parallel lists for chart types and series names. The formatter then:
1. Computes the global X-axis category list by taking the union of all X values across all results (preserving first-seen order; no sorting).
2. Builds one data series per logical series name.
   - For 2-column rows `[y, x]`, the series name comes from the provided `chartNames[i]`.
   - For 3-column rows `[y, x1, x2]` (double group-by), each distinct `x2` value becomes a separate series name.
3. Converts rows into values per X category, inserting `null` for missing pairs.
4. Encodes series payloads according to the requested chart type (e.g., array-of-numbers for line, array-of-objects for pie).

The final response is a `HighChartsJsonResponse` containing:
- `dataSeries`: series payloads in the same order as series names
- `xAxisCategories`: the full union of X values
- `seriesNames`: ordered list of series names
- `seriesTypes`: chart type per series

---

## Inputs and assumptions
- Each `Result` contains rows of either:
  - `[y, x]` (two columns), or
  - `[y, x1, x2]` (three columns, i.e., a two-level group-by)
- All results in the batch are expected to follow these formats consistently; otherwise a `DataFormationException` is thrown.
- `chartsType` and `chartNames` are passed alongside `dbAccessResults` and are intended to be 1:1 with the list of results.
- Drilldown is supported only in single-result flows; multi-result (merged) flows do not apply drilldown.

Key classes/methods:
- `HighChartsDataFormatter#toJsonResponse(List<Result> dbAccessResults, Object... args)`
- `HighChartsDataFormatter#multiToHighChartsJsonResponse(...)`
- `DataFormatter#getXAxisCategories(List<Result> dbAccessResults, boolean sort)`

---

## X-axis category collection
- The formatter scans all results and collects every distinct X value it sees into a `LinkedHashSet`, preserving insertion order.
- In the HighCharts formatter, categories are fetched via a private convenience method that calls `getXAxisCategories(..., false)`, so no sorting is performed.
- Practical effect: the first time an X value appears (across the entire batch of results), that is the position it will occupy in the final X-axis.

Implications:
- If the upstream SQL applies `ORDER BY x` (or an equivalent), categories will reflect that order for the first result that introduces each X value.
- When using DBAccess query merging (CTEs + FULL OUTER JOIN + outer `ORDER BY x`), this typically results in a well-ordered, aligned X-axis across series. See `DBAccess/docs/MergedQueries.md`.

---

## Series construction rules
There are two pathways based on the row shape of each `Result`.

1) Two-column rows `[y, x]`
- The entire `Result` maps to a single series.
- The series name is taken from `chartNames[i]` (fallback: `"Series i"`).
- The series type is `chartsType[i]`.

2) Three-column rows `[y, x1, x2]` (double group-by)
- Each distinct `x2` value becomes a separate series name.
- All series derived from this `Result` take their type from `chartsType[i]`.
- For HighCharts graph chart types (`sankey`, `dependencywheel`), the single-result path is used instead of multi-series aggregation.

Missing data handling:
- For any X category that lacks a value in a given series, the formatter inserts `null` (or `{ name: x, y: null }` for pie) to preserve alignment.

---

## Chart-type specific payloads
- `area`, `bar`, `column`, `line`, `treemap`: encoded as an array of numeric values (nullable) aligned to `xAxisCategories`.
- `pie`: encoded as an array of `{ name: x, y: value }` objects, one per X category.
- Other/unsupported types: series entry is set to `null` (no data generated).

Numeric parsing:
- Values are converted via `NumberUtils.parseValue(...)` to maintain numeric fidelity (avoid scientific notation issues).

---

## Drilldown
- Drilldown support exists in single-query flows (see `singleToHighChartsJsonResponse` and `HCDoubleGroupBy(..., isDrilldown)`).
- The multi-query path does not use the drilldown flag.

---

## Behavior with empty results
- If a `Result` is empty but is expected to form a 2-column series, the formatter still pre-allocates the series entry using its provided name and type; the series will contain only `null` values across the computed `xAxisCategories`.

---

## Interaction with DBAccess query merging
The formatter works both when subqueries are executed separately and when DBAccess merges compatible subqueries into a single SQL (see `DBAccess/docs/MergedQueries.md`). The important points:
- Regardless of DB merging, the formatter consumes rows shaped as `[y, x]` or `[y, x1, x2]`.
- When DBAccess merges subqueries (CTEs + FULL OUTER JOIN + outer `ORDER BY x`), the resulting stream still presents rows strictly as `[y, x]` to the formatter (via an `UNION ALL` unpivot step), preserving the formatter’s expectations.
- Because the merged SQL applies an outer `ORDER BY x`, the formatter’s first-seen X values generally follow the intended sort order.

---

## Examples

### Example A — Two separate `[y, x]` results (two series)
Inputs:
- Results:
  - R1 rows: `(y=100, x=North)`, `(120, South)`
  - R2 rows: `(y=80,  x=North)`, `(95,  East)`
- `chartsType`: `[line, line]`
- `chartNames`: `["Series A", "Series B"]`

Processing:
- `xAxisCategories` = `[North, South, East]` (first seen order)
- Series A values aligned to categories: `[100, 120, null]`
- Series B values aligned: `[80, null, 95]`

### Example B — One double group-by result `[y, x1, x2]`
Inputs:
- Result rows: `(y=10, x1=Jan, x2=North)`, `(15, Jan, South)`, `(5, Feb, North)`
- `chartsType`: `[column]`
- `chartNames`: `["ignored for 3-col"]`

Processing:
- Series created: `North`, `South`
- `xAxisCategories` = `[Jan, Feb]`
- Series North: `[10, 5]`
- Series South: `[15, null]`

---

## Notes and edge cases
- If any row set is not 2- or 3-columns, the formatter throws `DataFormationException`.
- For pie charts, missing values become `{ name: x, y: null }` entries to maintain category alignment.
- If types and results list sizes do not match in multi-query mode, an exception is thrown.

---

## Pointers to code
- `HighChartsDataFormatter#multiToHighChartsJsonResponse` — core logic for multi-result formatting
- `HighChartsDataFormatter#singleToHighChartsJsonResponse` — single-result logic, including drilldown and graph types
- `DataFormatter#getXAxisCategories` — union of X values (with optional sort; HighCharts uses no sort)
