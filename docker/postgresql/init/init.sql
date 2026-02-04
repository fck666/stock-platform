CREATE EXTENSION IF NOT EXISTS pgcrypto;

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

CREATE TABLE IF NOT EXISTS market.index_membership (
    index_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    as_of_date DATE NOT NULL,
    date_first_added DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (index_id, security_id, as_of_date)
);

CREATE TABLE IF NOT EXISTS market.security_detail (
    security_id BIGINT PRIMARY KEY REFERENCES market.security(id) ON DELETE CASCADE,
    sector TEXT,
    sub_industry TEXT,
    headquarters TEXT,
    cik TEXT,
    founded TEXT,
    wiki_url TEXT,
    stooq_symbol TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
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

CREATE TABLE IF NOT EXISTS market.profile (
    id BIGSERIAL PRIMARY KEY,
    profile_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS market.trade_plan (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES market.profile(id) ON DELETE CASCADE,
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    direction TEXT NOT NULL CHECK (direction IN ('LONG', 'SHORT')),
    status TEXT NOT NULL CHECK (status IN ('PLANNED', 'OPEN', 'CLOSED', 'CANCELLED')) DEFAULT 'PLANNED',
    start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    entry_price NUMERIC(20,6),
    entry_low NUMERIC(20,6),
    entry_high NUMERIC(20,6),
    stop_price NUMERIC(20,6),
    target_price NUMERIC(20,6),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trade_plan_profile_status
    ON market.trade_plan (profile_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_trade_plan_security
    ON market.trade_plan (security_id, start_date);

CREATE TABLE IF NOT EXISTS market.alert_rule (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES market.profile(id) ON DELETE CASCADE,
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    rule_type TEXT NOT NULL CHECK (rule_type IN ('PRICE_BREAKOUT', 'MA_CROSS', 'VOLUME_SURGE')),
    enabled BOOLEAN NOT NULL DEFAULT true,
    price_level NUMERIC(20,6),
    price_direction TEXT CHECK (price_direction IN ('ABOVE', 'BELOW')),
    ma_period INTEGER CHECK (ma_period IN (20, 50, 200)),
    ma_direction TEXT CHECK (ma_direction IN ('ABOVE', 'BELOW')),
    volume_multiple NUMERIC(20,6),
    last_triggered_date DATE,
    last_triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_rule_profile_enabled
    ON market.alert_rule (profile_id, enabled, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_rule_security
    ON market.alert_rule (security_id);

CREATE TABLE IF NOT EXISTS market.alert_event (
    id BIGSERIAL PRIMARY KEY,
    alert_rule_id BIGINT NOT NULL REFERENCES market.alert_rule(id) ON DELETE CASCADE,
    bar_date DATE NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (alert_rule_id, bar_date)
);

CREATE INDEX IF NOT EXISTS idx_alert_event_rule_created
    ON market.alert_event (alert_rule_id, created_at DESC);

CREATE SCHEMA IF NOT EXISTS iam;

CREATE TABLE IF NOT EXISTS iam.users (
    id uuid primary key default gen_random_uuid(),
    username text not null unique,
    status text not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

CREATE TABLE IF NOT EXISTS iam.identities (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references iam.users (id) on delete cascade,
    provider text not null,
    provider_uid text not null,
    password_hash text,
    created_at timestamptz not null default now(),
    unique (provider, provider_uid)
);

CREATE TABLE IF NOT EXISTS iam.roles (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    created_at timestamptz not null default now()
);

CREATE TABLE IF NOT EXISTS iam.permissions (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    created_at timestamptz not null default now()
);

CREATE TABLE IF NOT EXISTS iam.user_roles (
    user_id uuid not null references iam.users (id) on delete cascade,
    role_id uuid not null references iam.roles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS iam.role_permissions (
    role_id uuid not null references iam.roles (id) on delete cascade,
    permission_id uuid not null references iam.permissions (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS iam.refresh_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references iam.users (id) on delete cascade,
    token_hash text not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

INSERT INTO iam.roles (code, name) VALUES ('admin', '管理员') ON CONFLICT (code) DO NOTHING;
INSERT INTO iam.roles (code, name) VALUES ('user', '普通用户') ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.permissions (code, name) VALUES ('data.sync.execute', '执行数据同步') ON CONFLICT (code) DO NOTHING;
INSERT INTO iam.permissions (code, name) VALUES ('admin.stock.write', '管理股票') ON CONFLICT (code) DO NOTHING;
INSERT INTO iam.permissions (code, name) VALUES ('admin.index.write', '管理指数') ON CONFLICT (code) DO NOTHING;
INSERT INTO iam.permissions (code, name) VALUES ('iam.manage', '管理账号与权限') ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r, iam.permissions p
WHERE r.code = 'admin' AND p.code IN ('data.sync.execute', 'admin.stock.write', 'admin.index.write', 'iam.manage')
ON CONFLICT DO NOTHING;
