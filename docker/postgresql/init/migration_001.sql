-- Generic index membership table
CREATE TABLE IF NOT EXISTS market.index_membership (
    index_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    security_id BIGINT NOT NULL REFERENCES market.security(id) ON DELETE CASCADE,
    as_of_date DATE NOT NULL,
    date_first_added DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (index_id, security_id, as_of_date)
);

-- Generic security details (sector, etc.)
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

-- Migrate data from sp500_membership
DO $$
DECLARE
    sp500_id BIGINT;
BEGIN
    -- Ensure S&P 500 index exists in security table
    INSERT INTO market.security (security_type, canonical_symbol, name)
    VALUES ('INDEX', '^SPX', 'S&P 500')
    ON CONFLICT (security_type, canonical_symbol) DO UPDATE SET name = EXCLUDED.name
    RETURNING id INTO sp500_id;

    -- Migrate membership
    INSERT INTO market.index_membership (index_id, security_id, as_of_date, date_first_added)
    SELECT sp500_id, security_id, as_of_date, date_first_added
    FROM market.sp500_membership
    ON CONFLICT DO NOTHING;

    -- Migrate details
    INSERT INTO market.security_detail (security_id, sector, sub_industry, headquarters, cik, founded, wiki_url, stooq_symbol)
    SELECT DISTINCT ON (security_id) 
        security_id, gics_sector, gics_sub_industry, headquarters, cik, founded, wiki_url, stooq_symbol
    FROM market.sp500_membership
    ORDER BY security_id, as_of_date DESC
    ON CONFLICT (security_id) DO UPDATE SET
        sector = EXCLUDED.sector,
        sub_industry = EXCLUDED.sub_industry,
        headquarters = EXCLUDED.headquarters,
        cik = EXCLUDED.cik,
        founded = EXCLUDED.founded,
        wiki_url = EXCLUDED.wiki_url,
        stooq_symbol = EXCLUDED.stooq_symbol;
END $$;
