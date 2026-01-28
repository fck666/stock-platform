-- Migration 004: Replace Stooq-based fundamentals/dividends storage with Yahoo-based tables
-- This migration intentionally clears previously ingested fundamentals/dividends.

-- 1) Drop old corporate actions table (was used for Stooq HTML scraping)
DROP TABLE IF EXISTS market.dividend;

-- 2) Remove denormalized fundamentals columns from security_detail (if they exist)
ALTER TABLE market.security_detail DROP COLUMN IF EXISTS shares_outstanding;
ALTER TABLE market.security_detail DROP COLUMN IF EXISTS market_cap;

-- 3) New corporate actions table (dividends + splits)
CREATE TABLE IF NOT EXISTS market.corporate_action (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    ex_date DATE NOT NULL,
    action_type TEXT NOT NULL CHECK (action_type IN ('DIVIDEND', 'SPLIT')),
    cash_amount NUMERIC(20,6),
    currency TEXT,
    split_numerator INTEGER,
    split_denominator INTEGER,
    source TEXT NOT NULL DEFAULT 'yahoo',
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (security_id, ex_date, action_type)
);

CREATE INDEX IF NOT EXISTS idx_corporate_action_security_date
    ON market.corporate_action (security_id, ex_date);

-- 4) New fundamentals snapshot table (Yahoo provides point-in-time fundamentals)
CREATE TABLE IF NOT EXISTS market.fundamental_snapshot (
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    as_of_date DATE NOT NULL,
    shares_outstanding BIGINT,
    float_shares BIGINT,
    market_cap NUMERIC(20,2),
    currency TEXT,
    source TEXT NOT NULL DEFAULT 'yahoo',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (security_id, as_of_date, source)
);

CREATE INDEX IF NOT EXISTS idx_fundamental_snapshot_security_date
    ON market.fundamental_snapshot (security_id, as_of_date);

