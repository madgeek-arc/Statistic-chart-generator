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
-- Seed: run db/seed.sql separately to migrate existing JSON / .properties
-- data into these tables (safe to skip if starting fresh).
-- -----------------------------------------------------------------------
