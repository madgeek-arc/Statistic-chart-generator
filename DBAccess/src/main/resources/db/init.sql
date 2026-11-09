-- Bootstrap config tables for statstool dynamic configuration.
-- Run once on the config-db datasource (the PostgreSQL DB referenced by config-db in application.yml).
-- All statements are idempotent (IF NOT EXISTS / ON CONFLICT DO NOTHING).

CREATE TABLE IF NOT EXISTS statstool_datasource (
    id              VARCHAR(100) PRIMARY KEY,
    url             VARCHAR(500) NOT NULL,
    driver_class    VARCHAR(200) NOT NULL,
    username        VARCHAR(100),
    password        VARCHAR(200),
    max_pool_size   INT     DEFAULT 10,
    conn_timeout_ms INT     DEFAULT 20000,
    enabled         BOOLEAN DEFAULT TRUE,
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS statstool_mapping_profile (
    name            VARCHAR(100) PRIMARY KEY,
    description     TEXT,
    usage_hint      TEXT,
    shareholders    TEXT,       -- JSON array stored as text, e.g. ["All"]
    complexity      INT     DEFAULT 0,
    is_primary      BOOLEAN DEFAULT FALSE,
    is_hidden       BOOLEAN DEFAULT FALSE,
    mapping_content TEXT    NOT NULL,  -- full Mapping JSON (entities + relations)
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS statstool_named_query (
    name        VARCHAR(200) PRIMARY KEY,
    query       TEXT NOT NULL,
    description TEXT,
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- -----------------------------------------------------------------------
-- Cache schema — holds transient data; no migration needed.
-- -----------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS cache;

GRANT ALL ON SCHEMA cache TO statstool;

CREATE TABLE IF NOT EXISTS cache.cache_entry (
    key          VARCHAR(64)  NOT NULL,
    result       TEXT         NOT NULL,
    shadow       TEXT,
    query        TEXT         NOT NULL,
    created      TIMESTAMP    DEFAULT NOW() NOT NULL,
    updated      TIMESTAMP    DEFAULT NOW() NOT NULL,
    total_hits   INT          DEFAULT 0 NOT NULL,
    session_hits INT          DEFAULT 0 NOT NULL,
    pinned       BOOLEAN      DEFAULT FALSE NOT NULL,
    exectime     INT          DEFAULT 0 NOT NULL,
    queuetime    INT          DEFAULT 0 NOT NULL,
    profile      VARCHAR(255) NOT NULL,
    fresh        BOOLEAN      DEFAULT FALSE NOT NULL,
    PRIMARY KEY (key)
);

CREATE TABLE IF NOT EXISTS cache.nl_sql_cache (
    profile            VARCHAR(255) NOT NULL,
    canonical_nl       TEXT         NOT NULL,
    sql_query          TEXT         NOT NULL,
    parameters         TEXT,
    db_id              VARCHAR(255),
    schema_fingerprint VARCHAR(64)  DEFAULT '' NOT NULL,
    description        TEXT         DEFAULT '' NOT NULL,
    created            TIMESTAMP    DEFAULT NOW() NOT NULL,
    PRIMARY KEY (profile, canonical_nl)
);

CREATE TABLE IF NOT EXISTS cache.nl_options_cache (
    library          VARCHAR(255) NOT NULL,
    canonical_desc   TEXT         NOT NULL,
    options_json     TEXT         NOT NULL,
    prompt_version   VARCHAR(64)  DEFAULT '1' NOT NULL,
    created          TIMESTAMP    DEFAULT NOW() NOT NULL,
    PRIMARY KEY (library, canonical_desc)
);

GRANT ALL ON ALL TABLES IN SCHEMA cache TO statstool;

-- -----------------------------------------------------------------------
-- Seed: run db/seed.sql separately to migrate existing JSON / .properties
-- data into these tables (safe to skip if starting fresh).
-- -----------------------------------------------------------------------
