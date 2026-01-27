CREATE SCHEMA IF NOT EXISTS market;

CREATE TABLE IF NOT EXISTS market.security (
    id BIGSERIAL PRIMARY KEY,
    security_type TEXT NOT NULL CHECK (security_type IN ('STOCK', 'INDEX', 'ETF', 'FUND')),
    canonical_symbol TEXT NOT NULL,
    name TEXT,
    exchange TEXT,
    currency TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (security_type, canonical_symbol)
);

CREATE TABLE IF NOT EXISTS market.security_identifier (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    identifier TEXT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, identifier)
);

CREATE INDEX IF NOT EXISTS idx_security_identifier_security_provider
    ON market.security_identifier (security_id, provider);

CREATE TABLE IF NOT EXISTS market.sp500_membership (
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    as_of_date DATE NOT NULL,
    security_name TEXT,
    gics_sector TEXT,
    gics_sub_industry TEXT,
    headquarters TEXT,
    date_first_added DATE,
    cik TEXT,
    founded TEXT,
    wiki_url TEXT,
    stooq_symbol TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (security_id, as_of_date)
);

CREATE TABLE IF NOT EXISTS market.wiki_summary (
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    lang TEXT NOT NULL,
    source_url TEXT NOT NULL,
    title TEXT,
    description TEXT,
    extract TEXT,
    page_id BIGINT,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (security_id, lang)
);

CREATE TABLE IF NOT EXISTS market.price_bar (
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    interval TEXT NOT NULL CHECK (interval IN ('1d', '1w', '1m', '1q', '1y')),
    bar_date DATE NOT NULL,
    open NUMERIC(20,6),
    high NUMERIC(20,6),
    low NUMERIC(20,6),
    close NUMERIC(20,6),
    volume BIGINT,
    currency TEXT,
    source TEXT NOT NULL DEFAULT 'stooq',
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (security_id, interval, bar_date)
);

CREATE INDEX IF NOT EXISTS idx_price_bar_security_interval_date
    ON market.price_bar (security_id, interval, bar_date);

CREATE INDEX IF NOT EXISTS idx_price_bar_interval_date
    ON market.price_bar (interval, bar_date);

