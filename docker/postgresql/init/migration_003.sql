-- Add dividend table
CREATE TABLE IF NOT EXISTS market.dividend (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    ex_date DATE NOT NULL,
    amount NUMERIC(20,6),
    dividend_type TEXT, -- e.g., 'Dividend', 'Split'
    raw_text TEXT,      -- The original text from Stooq, e.g., 'Dividend 0.47%'
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (security_id, ex_date, dividend_type)
);

-- Add fundamental columns to security_detail
ALTER TABLE market.security_detail 
ADD COLUMN IF NOT EXISTS shares_outstanding BIGINT,
ADD COLUMN IF NOT EXISTS market_cap NUMERIC(20,2);
