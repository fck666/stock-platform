from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from typing import Iterable

import pandas as pd
import requests

from collectors.sp500_list import fetch_sp500_companies, filter_symbols
from collectors.stooq_price import fetch_prices_range
from collectors.wiki_info import fetch_company_wiki_summaries
from db.connection import DbConfig, get_connection
from db.stock_repo import StockRepository
from utils.date_utils import parse_ymd, yesterday_ymd


log = logging.getLogger(__name__)


DEFAULT_INDICES: dict[str, dict[str, str]] = {
    "^SPX": {"name": "S&P 500", "type": "INDEX", "stooq": "^spx"},
    "^DJI": {"name": "Dow Jones Industrial", "type": "INDEX", "stooq": "^dji"},
    "^NDQ": {"name": "Nasdaq Composite", "type": "INDEX", "stooq": "^ndq"},
    "IWM": {"name": "Russell 2000 (ETF proxy)", "type": "ETF", "stooq": "iwm.us"},
    "VIXY": {"name": "VIX (ETF proxy)", "type": "ETF", "stooq": "vixy.us"},
}


def _parse_optional_date(value: str | None) -> date | None:
    if value is None:
        return None
    s = str(value).strip()
    if s == "" or s.lower() in {"nan", "none", "—", "-"}:
        return None
    try:
        return parse_ymd(s)
    except Exception:
        return None


def _interval_to_freq(interval: str) -> str:
    mapping = {"1d": "d", "1w": "w", "1m": "m", "1q": "q", "1y": "y"}
    if interval not in mapping:
        raise ValueError("interval must be one of: 1d, 1w, 1m, 1q, 1y")
    return mapping[interval]


def _iter_date_ranges(start: date, end: date, max_days: int) -> Iterable[tuple[date, date]]:
    if max_days <= 0:
        raise ValueError("max_days must be positive")
    cur = start
    while cur <= end:
        chunk_end = min(end, cur + timedelta(days=max_days - 1))
        yield cur, chunk_end
        cur = chunk_end + timedelta(days=1)


@dataclass(frozen=True)
class WikiSyncResult:
    securities_upserted: int
    wiki_upserted: int


def sync_sp500_wiki_to_db(
    *,
    db_dsn: str,
    symbols: Iterable[str] | None,
    limit: int | None,
    wiki_lang: str,
    http_timeout_seconds: float,
    user_agent: str,
) -> WikiSyncResult:
    with requests.Session() as session:
        companies = fetch_sp500_companies(session, timeout=http_timeout_seconds, user_agent=user_agent)
        companies = filter_symbols(companies, symbols=symbols, limit=limit)

        wiki_summaries = fetch_company_wiki_summaries(
            session=session,
            companies_df=companies,
            lang=wiki_lang,
            timeout=http_timeout_seconds,
            user_agent=user_agent,
            limit=limit,
        )

    as_of = datetime.utcnow().date()
    with get_connection(DbConfig(dsn=db_dsn)) as conn:
        repo = StockRepository(conn)
        repo.ensure_search_path()

        securities_upserted = 0
        symbol_to_security_id: dict[str, int] = {}
        for _, r in companies.iterrows():
            canonical_symbol = str(r["symbol"]).strip().upper()
            security_name = str(r.get("security") or "").strip() or None
            security_id = repo.upsert_security(
                security_type="STOCK",
                canonical_symbol=canonical_symbol,
                name=security_name,
            )
            securities_upserted += 1
            symbol_to_security_id[canonical_symbol] = security_id

            stooq_symbol = str(r.get("stooq_symbol") or "").strip().lower() or None
            wiki_url = str(r.get("wiki_url") or "").strip() or None
            if stooq_symbol:
                repo.upsert_security_identifier(security_id=security_id, provider="stooq", identifier=stooq_symbol)
            if wiki_url:
                repo.upsert_security_identifier(security_id=security_id, provider="wikipedia", identifier=wiki_url)

            repo.upsert_sp500_membership(
                security_id=security_id,
                as_of_date=as_of,
                security_name=security_name,
                gics_sector=str(r.get("gics_sector") or "").strip() or None,
                gics_sub_industry=str(r.get("gics_sub_industry") or "").strip() or None,
                headquarters=str(r.get("headquarters") or "").strip() or None,
                date_first_added=_parse_optional_date(r.get("date_first_added")),
                cik=str(r.get("cik") or "").strip() or None,
                founded=str(r.get("founded") or "").strip() or None,
                wiki_url=wiki_url,
                stooq_symbol=stooq_symbol,
            )

        wiki_upserted = 0
        if not wiki_summaries.empty:
            for _, r in wiki_summaries.iterrows():
                canonical_symbol = str(r["symbol"]).strip().upper()
                security_id = symbol_to_security_id.get(canonical_symbol)
                if security_id is None:
                    continue
                repo.upsert_wiki_summary(
                    security_id=security_id,
                    lang=wiki_lang,
                    source_url=str(r.get("wiki_url") or "").strip(),
                    title=r.get("title"),
                    description=r.get("description"),
                    extract=r.get("extract"),
                    page_id=int(r["page_id"]) if pd.notna(r.get("page_id")) else None,
                )
                wiki_upserted += 1

    log.info("Upserted %s securities and %s wiki summaries", securities_upserted, wiki_upserted)
    return WikiSyncResult(securities_upserted=securities_upserted, wiki_upserted=wiki_upserted)


@dataclass(frozen=True)
class PriceSyncResult:
    securities_scanned: int
    bars_upserted: int


def _df_to_price_rows(
    df: pd.DataFrame,
    *,
    security_id: int,
    interval: str,
    source: str,
    currency: str | None = None,
) -> list[StockRepository.PriceBarRow]:
    rows: list[StockRepository.PriceBarRow] = []
    for _, r in df.iterrows():
        d = r["date"]
        if isinstance(d, datetime):
            bar_date = d.date()
        else:
            bar_date = pd.to_datetime(d).date()

        def _f(x):
            return float(x) if pd.notna(x) else None

        def _i(x):
            return int(x) if pd.notna(x) else None

        rows.append(
            StockRepository.PriceBarRow(
                security_id=security_id,
                interval=interval,
                bar_date=bar_date,
                open=_f(r.get("open")),
                high=_f(r.get("high")),
                low=_f(r.get("low")),
                close=_f(r.get("close")),
                volume=_i(r.get("volume")),
                currency=currency,
                source=source,
            )
        )
    return rows


def sync_indices_prices_to_db(
    *,
    db_dsn: str,
    start_date: str,
    end_date: str,
    interval: str,
    http_timeout_seconds: float,
    user_agent: str,
) -> PriceSyncResult:
    start = parse_ymd(start_date)
    end = parse_ymd(end_date)
    freq = _interval_to_freq(interval)

    bars_upserted = 0
    securities_scanned = 0
    with requests.Session() as http, get_connection(DbConfig(dsn=db_dsn)) as conn:
        repo = StockRepository(conn)
        repo.ensure_search_path()

        for canonical_symbol, meta in DEFAULT_INDICES.items():
            securities_scanned += 1
            security_id = repo.upsert_security(
                security_type=meta["type"],
                canonical_symbol=canonical_symbol,
                name=meta["name"],
            )
            stooq_symbol = meta["stooq"]
            repo.upsert_security_identifier(security_id=security_id, provider="stooq", identifier=stooq_symbol)

            max_date = repo.get_max_bar_date(security_id=security_id, interval=interval)
            effective_start = start
            if max_date is not None:
                effective_start = max(effective_start, max_date + timedelta(days=1))
            if effective_start > end:
                continue

            for chunk_start, chunk_end in _iter_date_ranges(effective_start, end, max_days=4000):
                try:
                    df = fetch_prices_range(
                        session=http,
                        stooq_symbol=stooq_symbol,
                        start_date=chunk_start,
                        end_date=chunk_end,
                        freq=freq,
                        timeout=http_timeout_seconds,
                        user_agent=user_agent,
                        pause_seconds=0.1,
                    )
                except Exception as e:
                    log.warning("Failed fetching %s (%s) %s..%s: %s", canonical_symbol, stooq_symbol, chunk_start, chunk_end, e)
                    if "rate limit exceeded" in str(e).lower() or "daily hits limit" in str(e).lower():
                        raise
                    continue

                rows = _df_to_price_rows(df, security_id=security_id, interval=interval, source="stooq")
                bars_upserted += repo.upsert_price_bars(rows)

    return PriceSyncResult(securities_scanned=securities_scanned, bars_upserted=bars_upserted)


def sync_sp500_prices_to_db(
    *,
    db_dsn: str,
    start_date: str,
    end_date: str,
    interval: str,
    symbols: Iterable[str] | None,
    limit: int | None,
    http_timeout_seconds: float,
    user_agent: str,
    include_indices: bool = True,
) -> PriceSyncResult:
    start = parse_ymd(start_date)
    end = parse_ymd(end_date)
    freq = _interval_to_freq(interval)

    bars_upserted = 0
    securities_scanned = 0
    with requests.Session() as http, get_connection(DbConfig(dsn=db_dsn)) as conn:
        repo = StockRepository(conn)
        repo.ensure_search_path()

        constituents = repo.list_latest_sp500_constituents(symbols=symbols, limit=limit)
        if not constituents:
            with requests.Session() as session:
                companies = fetch_sp500_companies(session, timeout=http_timeout_seconds, user_agent=user_agent)
                companies = filter_symbols(companies, symbols=symbols, limit=limit)
            fallback: list[StockRepository.Sp500Constituent] = []
            for _, r in companies.iterrows():
                canonical_symbol = str(r["symbol"]).strip().upper()
                security_name = str(r.get("security") or "").strip() or None
                stooq_symbol = str(r.get("stooq_symbol") or "").strip().lower()
                if stooq_symbol == "":
                    continue
                security_id = repo.upsert_security(
                    security_type="STOCK",
                    canonical_symbol=canonical_symbol,
                    name=security_name,
                )
                repo.upsert_security_identifier(security_id=security_id, provider="stooq", identifier=stooq_symbol)
                fallback.append(
                    StockRepository.Sp500Constituent(
                        security_id=security_id,
                        canonical_symbol=canonical_symbol,
                        stooq_symbol=stooq_symbol,
                        security_name=security_name,
                    )
                )
            constituents = fallback

        for c in constituents:
            securities_scanned += 1
            canonical_symbol = c.canonical_symbol
            stooq_symbol = c.stooq_symbol
            security_id = c.security_id

            max_date = repo.get_max_bar_date(security_id=security_id, interval=interval)
            effective_start = start
            if max_date is not None:
                effective_start = max(effective_start, max_date + timedelta(days=1))
            if effective_start > end:
                continue

            for chunk_start, chunk_end in _iter_date_ranges(effective_start, end, max_days=4000):
                try:
                    df = fetch_prices_range(
                        session=http,
                        stooq_symbol=stooq_symbol,
                        start_date=chunk_start,
                        end_date=chunk_end,
                        freq=freq,
                        timeout=http_timeout_seconds,
                        user_agent=user_agent,
                        pause_seconds=0.1,
                    )
                except Exception as e:
                    log.warning(
                        "Failed fetching %s (%s) %s..%s: %s",
                        canonical_symbol,
                        stooq_symbol,
                        chunk_start,
                        chunk_end,
                        e,
                    )
                    if "rate limit exceeded" in str(e).lower() or "daily hits limit" in str(e).lower():
                        raise
                    continue

                rows = _df_to_price_rows(df, security_id=security_id, interval=interval, source="stooq")
                bars_upserted += repo.upsert_price_bars(rows)

    if include_indices:
        idx_res = sync_indices_prices_to_db(
            db_dsn=db_dsn,
            start_date=start_date,
            end_date=end_date,
            interval=interval,
            http_timeout_seconds=http_timeout_seconds,
            user_agent=user_agent,
        )
        securities_scanned += idx_res.securities_scanned
        bars_upserted += idx_res.bars_upserted

    return PriceSyncResult(securities_scanned=securities_scanned, bars_upserted=bars_upserted)


def sync_sp500_daily_incremental(
    *,
    db_dsn: str,
    interval: str = "1d",
    symbols: Iterable[str] | None = None,
    limit: int | None = None,
    http_timeout_seconds: float,
    user_agent: str,
    include_indices: bool = True,
) -> PriceSyncResult:
    end_date = yesterday_ymd()
    start_floor = "1900-01-01"
    return sync_sp500_prices_to_db(
        db_dsn=db_dsn,
        start_date=start_floor,
        end_date=end_date,
        interval=interval,
        symbols=symbols,
        limit=limit,
        http_timeout_seconds=http_timeout_seconds,
        user_agent=user_agent,
        include_indices=include_indices,
    )
