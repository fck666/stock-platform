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
