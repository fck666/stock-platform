from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Iterable

import json
import psycopg2
from psycopg2.extras import execute_values

class StockRepository:
    def __init__(self, conn: psycopg2.extensions.connection) -> None:
        self._conn = conn

    def ensure_search_path(self) -> None:
        with self._conn.cursor() as cur:
            cur.execute("SET search_path TO market, public;")
        self._conn.commit()

    def upsert_security(
        self,
        *,
        security_type: str,
        canonical_symbol: str,
        name: str | None,
        exchange: str | None = None,
        currency: str | None = None,
    ) -> int:
        sql = """
        INSERT INTO security (security_type, canonical_symbol, name, exchange, currency)
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT (security_type, canonical_symbol)
        DO UPDATE SET
            name = COALESCE(EXCLUDED.name, security.name),
            exchange = COALESCE(EXCLUDED.exchange, security.exchange),
            currency = COALESCE(EXCLUDED.currency, security.currency),
            updated_at = now()
        RETURNING id;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_type, canonical_symbol, name, exchange, currency))
            security_id = int(cur.fetchone()[0])
        self._conn.commit()
        return security_id

    def upsert_security_identifier(
        self,
        *,
        security_id: int,
        provider: str,
        identifier: str,
        is_primary: bool = True,
    ) -> None:
        sql = """
        INSERT INTO security_identifier (security_id, provider, identifier, is_primary)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (provider, identifier)
        DO UPDATE SET
            security_id = EXCLUDED.security_id,
            is_primary = EXCLUDED.is_primary;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, provider, identifier, is_primary))
        self._conn.commit()

    def upsert_index_membership(
        self,
        *,
        index_id: int,
        security_id: int,
        as_of_date: date,
        date_first_added: date | None = None,
    ) -> None:
        sql = """
        INSERT INTO index_membership (index_id, security_id, as_of_date, date_first_added)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (index_id, security_id, as_of_date)
        DO UPDATE SET
            date_first_added = EXCLUDED.date_first_added;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (index_id, security_id, as_of_date, date_first_added))
        self._conn.commit()

    def upsert_security_detail(
        self,
        *,
        security_id: int,
        sector: str | None = None,
        sub_industry: str | None = None,
        headquarters: str | None = None,
        cik: str | None = None,
        founded: str | None = None,
        wiki_url: str | None = None,
        stooq_symbol: str | None = None,
    ) -> None:
        sql = """
        INSERT INTO security_detail (
            security_id, sector, sub_industry, headquarters, cik, founded, wiki_url, stooq_symbol
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (security_id)
        DO UPDATE SET
            sector = COALESCE(EXCLUDED.sector, security_detail.sector),
            sub_industry = COALESCE(EXCLUDED.sub_industry, security_detail.sub_industry),
            headquarters = COALESCE(EXCLUDED.headquarters, security_detail.headquarters),
            cik = COALESCE(EXCLUDED.cik, security_detail.cik),
            founded = COALESCE(EXCLUDED.founded, security_detail.founded),
            wiki_url = COALESCE(EXCLUDED.wiki_url, security_detail.wiki_url),
            stooq_symbol = COALESCE(EXCLUDED.stooq_symbol, security_detail.stooq_symbol),
            updated_at = now();
        """
        with self._conn.cursor() as cur:
            cur.execute(
                sql,
                (
                    security_id,
                    sector,
                    sub_industry,
                    headquarters,
                    cik,
                    founded,
                    wiki_url,
                    stooq_symbol,
                ),
            )
        self._conn.commit()

    def upsert_sp500_membership(
        self,
        *,
        security_id: int,
        as_of_date: date,
        security_name: str | None,
        gics_sector: str | None,
        gics_sub_industry: str | None,
        headquarters: str | None,
        date_first_added: date | None,
        cik: str | None,
        founded: str | None,
        wiki_url: str | None,
        stooq_symbol: str | None,
    ) -> None:
        # Keep for backward compatibility but redirect to new tables
        sp500_id = self.upsert_security(security_type="INDEX", canonical_symbol="^SPX", name="S&P 500")
        self.upsert_index_membership(
            index_id=sp500_id,
            security_id=security_id,
            as_of_date=as_of_date,
            date_first_added=date_first_added,
        )
        self.upsert_security_detail(
            security_id=security_id,
            sector=gics_sector,
            sub_industry=gics_sub_industry,
            headquarters=headquarters,
            cik=cik,
            founded=founded,
            wiki_url=wiki_url,
            stooq_symbol=stooq_symbol,
        )

    def upsert_wiki_summary(
        self,
        *,
        security_id: int,
        lang: str,
        source_url: str,
        title: str | None,
        description: str | None,
        extract: str | None,
        page_id: int | None,
    ) -> None:
        sql = """
        INSERT INTO wiki_summary (security_id, lang, source_url, title, description, extract, page_id)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (security_id, lang)
        DO UPDATE SET
            source_url = EXCLUDED.source_url,
            title = EXCLUDED.title,
            description = EXCLUDED.description,
            extract = EXCLUDED.extract,
            page_id = EXCLUDED.page_id,
            updated_at = now(),
            fetched_at = now();
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, lang, source_url, title, description, extract, page_id))
        self._conn.commit()

    def get_stooq_symbol(self, security_id: int) -> str | None:
        sql = "SELECT identifier FROM security_identifier WHERE security_id = %s AND provider = 'stooq';"
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id,))
            row = cur.fetchone()
        return row[0] if row else None
    def get_security_id_by_identifier(self, *, provider: str, identifier: str) -> int | None:
        sql = "SELECT security_id FROM security_identifier WHERE provider = %s AND identifier = %s;"
        with self._conn.cursor() as cur:
            cur.execute(sql, (provider, identifier))
            row = cur.fetchone()
        return int(row[0]) if row else None

    def get_max_bar_date(self, *, security_id: int, interval: str) -> date | None:
        sql = "SELECT max(bar_date) FROM price_bar WHERE security_id = %s AND interval = %s;"
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, interval))
            row = cur.fetchone()
        return row[0] if row and row[0] is not None else None

    def delete_price_bars(self, *, security_id: int, interval: str) -> None:
        sql = "DELETE FROM price_bar WHERE security_id = %s AND interval = %s;"
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, interval))
        self._conn.commit()

    def get_price_bars_map(
        self, *, security_id: int, interval: str, start_date: date, end_date: date
    ) -> dict[date, float]:
        sql = """
        SELECT bar_date, close FROM price_bar 
        WHERE security_id = %s AND interval = %s AND bar_date BETWEEN %s AND %s;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, interval, start_date, end_date))
            rows = cur.fetchall()
        return {r[0]: float(r[1]) for r in rows if r[1] is not None}

    @dataclass(frozen=True)
    class Sp500Constituent:
        security_id: int
        canonical_symbol: str
        stooq_symbol: str
        security_name: str | None

    @dataclass(frozen=True)
    class IndexConstituent:
        security_id: int
        canonical_symbol: str
        stooq_symbol: str
        security_name: str | None

    def list_latest_index_constituents(
        self,
        *,
        index_symbol: str,
        symbols: Iterable[str] | None = None,
        limit: int | None = None,
    ) -> list["StockRepository.IndexConstituent"]:
        where = ["s.security_type = 'STOCK'", "idx.canonical_symbol = %s"]
        params: list[object] = [index_symbol]

        if symbols:
            symbol_list = [s.strip().upper() for s in symbols if s.strip()]
            where.append("s.canonical_symbol = ANY(%s)")
            params.append(symbol_list)

        sql = f"""
        WITH latest AS (
            SELECT m.index_id, max(m.as_of_date) AS as_of_date 
            FROM index_membership m
            JOIN security idx ON idx.id = m.index_id
            WHERE idx.canonical_symbol = %s
            GROUP BY m.index_id
        )
        SELECT
            s.id AS security_id,
            s.canonical_symbol,
            si.identifier AS stooq_symbol,
            s.name AS security_name
        FROM security s
        JOIN latest l ON TRUE
        JOIN index_membership m ON m.security_id = s.id AND m.index_id = l.index_id AND m.as_of_date = l.as_of_date
        JOIN security idx ON idx.id = m.index_id
        JOIN security_identifier si ON si.security_id = s.id AND si.provider = 'stooq'
        WHERE {" AND ".join(where)}
        ORDER BY s.canonical_symbol;
        """
        # Add index_symbol again for the WITH clause parameter
        params = [index_symbol] + params

        if limit is not None and limit > 0:
            sql = sql.rstrip().rstrip(";") + " LIMIT %s;"
            params.append(limit)

        out: list[StockRepository.IndexConstituent] = []
        with self._conn.cursor() as cur:
            cur.execute(sql, params)
            for security_id, canonical_symbol, stooq_symbol, security_name in cur.fetchall():
                out.append(
                    StockRepository.IndexConstituent(
                        security_id=int(security_id),
                        canonical_symbol=str(canonical_symbol),
                        stooq_symbol=str(stooq_symbol),
                        security_name=str(security_name) if security_name is not None else None,
                    )
                )
        return out

    def list_latest_sp500_constituents(
        self,
        *,
        symbols: Iterable[str] | None = None,
        limit: int | None = None,
    ) -> list["StockRepository.Sp500Constituent"]:
        # Backward compatibility
        constituents = self.list_latest_index_constituents(index_symbol="^SPX", symbols=symbols, limit=limit)
        return [
            StockRepository.Sp500Constituent(
                security_id=c.security_id,
                canonical_symbol=c.canonical_symbol,
                stooq_symbol=c.stooq_symbol,
                security_name=c.security_name,
            )
            for c in constituents
        ]

    @dataclass(frozen=True)
    class PriceBarRow:
        security_id: int
        interval: str
        bar_date: date
        open: float | None
        high: float | None
        low: float | None
        close: float | None
        volume: int | None
        currency: str | None
        source: str

    def upsert_corporate_action(
        self,
        *,
        security_id: int,
        ex_date: date,
        action_type: str,
        cash_amount: float | None = None,
        currency: str | None = None,
        split_numerator: int | None = None,
        split_denominator: int | None = None,
        source: str = "yahoo",
        raw_payload: dict | None = None,
    ) -> None:
        sql = """
        INSERT INTO corporate_action (
            security_id, ex_date, action_type, cash_amount, currency, split_numerator, split_denominator, source, raw_payload
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb)
        ON CONFLICT (security_id, ex_date, action_type)
        DO UPDATE SET
            cash_amount = EXCLUDED.cash_amount,
            currency = EXCLUDED.currency,
            split_numerator = EXCLUDED.split_numerator,
            split_denominator = EXCLUDED.split_denominator,
            source = EXCLUDED.source,
            raw_payload = EXCLUDED.raw_payload;
        """
        with self._conn.cursor() as cur:
            cur.execute(
                sql,
                (
                    security_id,
                    ex_date,
                    action_type,
                    cash_amount,
                    currency,
                    split_numerator,
                    split_denominator,
                    source,
                    json.dumps(raw_payload) if raw_payload is not None else None,
                ),
            )
        self._conn.commit()

    def upsert_fundamental_snapshot(
        self,
        *,
        security_id: int,
        as_of_date: date,
        shares_outstanding: int | None = None,
        float_shares: int | None = None,
        market_cap: float | None = None,
        currency: str | None = None,
        source: str = "yahoo",
    ) -> None:
        sql = """
        INSERT INTO fundamental_snapshot (
            security_id, as_of_date, shares_outstanding, float_shares, market_cap, currency, source
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (security_id, as_of_date, source)
        DO UPDATE SET
            shares_outstanding = EXCLUDED.shares_outstanding,
            float_shares = EXCLUDED.float_shares,
            market_cap = EXCLUDED.market_cap,
            currency = EXCLUDED.currency;
        """
        with self._conn.cursor() as cur:
            cur.execute(
                sql,
                (security_id, as_of_date, shares_outstanding, float_shares, market_cap, currency, source),
            )
        self._conn.commit()

    def has_fundamental_snapshot(
        self,
        *,
        security_id: int,
        as_of_date: date,
        source: str = "yahoo",
    ) -> bool:
        sql = """
        SELECT 1
        FROM fundamental_snapshot
        WHERE security_id = %s AND as_of_date = %s AND source = %s
        LIMIT 1;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, as_of_date, source))
            return cur.fetchone() is not None

    def has_any_fundamental_snapshot(
        self,
        *,
        security_id: int,
        as_of_date: date,
    ) -> bool:
        sql = """
        SELECT 1
        FROM fundamental_snapshot
        WHERE security_id = %s AND as_of_date = %s
        LIMIT 1;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, as_of_date))
            return cur.fetchone() is not None

    def get_latest_corporate_action_ex_date(
        self,
        *,
        security_id: int,
        source: str = "yahoo",
    ) -> date | None:
        sql = """
        SELECT max(ex_date) AS ex_date
        FROM corporate_action
        WHERE security_id = %s AND source = %s;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, source))
            r = cur.fetchone()
            return r[0] if r and r[0] is not None else None

    def count_corporate_actions(
        self,
        *,
        security_id: int,
        source: str = "yahoo",
    ) -> int:
        sql = """
        SELECT count(*) AS cnt
        FROM corporate_action
        WHERE security_id = %s AND source = %s;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, source))
            r = cur.fetchone()
            return int(r[0]) if r else 0

    def get_security_cik(self, *, security_id: int) -> str | None:
        sql = """
        SELECT cik
        FROM security_detail
        WHERE security_id = %s;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id,))
            r = cur.fetchone()
            if not r or r[0] is None:
                return None
            s = str(r[0]).strip()
            return s or None

    def get_latest_close(self, *, security_id: int, interval: str = "1d") -> tuple[date, float] | None:
        sql = """
        SELECT bar_date, close
        FROM price_bar
        WHERE security_id = %s AND interval = %s AND close IS NOT NULL
        ORDER BY bar_date DESC
        LIMIT 1;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id, interval))
            r = cur.fetchone()
            if not r or r[0] is None or r[1] is None:
                return None
            return (r[0], float(r[1]))

    def get_security_currency(self, *, security_id: int) -> str | None:
        sql = """
        SELECT currency
        FROM security
        WHERE id = %s;
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, (security_id,))
            r = cur.fetchone()
            if not r or r[0] is None:
                return None
            s = str(r[0]).strip()
            return s or None

    def upsert_price_bars(self, rows: Iterable["StockRepository.PriceBarRow"]) -> int:
        rows_list = list(rows)
        if not rows_list:
            return 0

        sql = """
        INSERT INTO price_bar (
            security_id, interval, bar_date, open, high, low, close, volume, currency, source
        )
        VALUES %s
        ON CONFLICT (security_id, interval, bar_date)
        DO UPDATE SET
            open = EXCLUDED.open,
            high = EXCLUDED.high,
            low = EXCLUDED.low,
            close = EXCLUDED.close,
            volume = EXCLUDED.volume,
            currency = EXCLUDED.currency,
            source = EXCLUDED.source,
            ingested_at = now();
        """

        values = [
            (
                r.security_id,
                r.interval,
                r.bar_date,
                r.open,
                r.high,
                r.low,
                r.close,
                r.volume,
                r.currency,
                r.source,
            )
            for r in rows_list
        ]
        with self._conn.cursor() as cur:
            execute_values(cur, sql, values, page_size=2000)
        self._conn.commit()
        return len(rows_list)
