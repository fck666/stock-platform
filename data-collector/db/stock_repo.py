from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Iterable

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
        sql = """
        INSERT INTO sp500_membership (
            security_id, as_of_date, security_name, gics_sector, gics_sub_industry,
            headquarters, date_first_added, cik, founded, wiki_url, stooq_symbol
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (security_id, as_of_date)
        DO UPDATE SET
            security_name = EXCLUDED.security_name,
            gics_sector = EXCLUDED.gics_sector,
            gics_sub_industry = EXCLUDED.gics_sub_industry,
            headquarters = EXCLUDED.headquarters,
            date_first_added = EXCLUDED.date_first_added,
            cik = EXCLUDED.cik,
            founded = EXCLUDED.founded,
            wiki_url = EXCLUDED.wiki_url,
            stooq_symbol = EXCLUDED.stooq_symbol;
        """
        with self._conn.cursor() as cur:
            cur.execute(
                sql,
                (
                    security_id,
                    as_of_date,
                    security_name,
                    gics_sector,
                    gics_sub_industry,
                    headquarters,
                    date_first_added,
                    cik,
                    founded,
                    wiki_url,
                    stooq_symbol,
                ),
            )
        self._conn.commit()

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

    @dataclass(frozen=True)
    class Sp500Constituent:
        security_id: int
        canonical_symbol: str
        stooq_symbol: str
        security_name: str | None

    def list_latest_sp500_constituents(
        self,
        *,
        symbols: Iterable[str] | None = None,
        limit: int | None = None,
    ) -> list["StockRepository.Sp500Constituent"]:
        where = ["s.security_type = 'STOCK'"]
        params: list[object] = []

        if symbols:
            symbol_list = [s.strip().upper() for s in symbols if s.strip()]
            where.append("s.canonical_symbol = ANY(%s)")
            params.append(symbol_list)

        sql = f"""
        WITH latest AS (
            SELECT max(as_of_date) AS as_of_date FROM sp500_membership
        )
        SELECT
            s.id AS security_id,
            s.canonical_symbol,
            si.identifier AS stooq_symbol,
            s.name AS security_name
        FROM security s
        JOIN latest l ON TRUE
        JOIN sp500_membership m ON m.security_id = s.id AND m.as_of_date = l.as_of_date
        JOIN security_identifier si ON si.security_id = s.id AND si.provider = 'stooq'
        WHERE {" AND ".join(where)}
        ORDER BY s.canonical_symbol;
        """

        if limit is not None and limit > 0:
            sql = sql.rstrip().rstrip(";") + " LIMIT %s;"
            params.append(limit)

        out: list[StockRepository.Sp500Constituent] = []
        with self._conn.cursor() as cur:
            cur.execute(sql, params)
            for security_id, canonical_symbol, stooq_symbol, security_name in cur.fetchall():
                out.append(
                    StockRepository.Sp500Constituent(
                        security_id=int(security_id),
                        canonical_symbol=str(canonical_symbol),
                        stooq_symbol=str(stooq_symbol),
                        security_name=str(security_name) if security_name is not None else None,
                    )
                )
        return out

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
