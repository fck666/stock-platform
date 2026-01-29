from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from typing import Iterable

import pandas as pd
import requests

from collectors.sp500_list import fetch_sp500_companies, filter_symbols
from collectors.hangseng_list import fetch_hsi_components, fetch_hstech_components
from collectors.stooq_price import fetch_prices_range
from collectors.stooq_operations import fetch_stooq_fundamentals
from collectors.eodhd import (
    canonical_to_eodhd_symbol,
    fetch_eodhd_dividends,
    fetch_eodhd_fundamentals,
    fetch_eodhd_splits,
)
from collectors.yahoo_finance import (
    canonical_to_yahoo_symbol,
    fetch_yahoo_corporate_actions,
    fetch_yahoo_fundamentals,
)
from collectors.sec_facts import fetch_sec_shares_outstanding
from collectors.wiki_info import fetch_company_wiki_summaries
from db.connection import DbConfig, get_connection
from db.stock_repo import StockRepository
from utils.date_utils import parse_ymd


log = logging.getLogger(__name__)


DEFAULT_INDICES: dict[str, dict[str, str]] = {
    "^SPX": {"name": "S&P 500", "type": "INDEX", "stooq": "^spx"},
    "^DJI": {"name": "Dow Jones Industrial", "type": "INDEX", "stooq": "^dji"},
    "^NDQ": {"name": "Nasdaq Composite", "type": "INDEX", "stooq": "^ndq"},
    "^HSI": {"name": "Hang Seng Index", "type": "INDEX", "stooq": "^HSI"},
    "^HSTECH": {"name": "Hang Seng TECH Index", "type": "INDEX", "stooq": "^HSTECH"},
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


def sync_index_wiki_to_db(
    *,
    db_dsn: str,
    index_symbol: str,
    symbols: Iterable[str] | None,
    limit: int | None,
    wiki_lang: str,
    http_timeout_seconds: float,
    user_agent: str,
) -> WikiSyncResult:
    with requests.Session() as session:
        if index_symbol == "^SPX":
            companies = fetch_sp500_companies(session, timeout=http_timeout_seconds, user_agent=user_agent)
        elif index_symbol == "^HSI":
            companies = fetch_hsi_components(session, timeout=http_timeout_seconds, user_agent=user_agent)
        elif index_symbol == "^HSTECH":
            companies = fetch_hstech_components(session, timeout=http_timeout_seconds, user_agent=user_agent)
        else:
            raise ValueError(f"Unsupported index for wiki sync: {index_symbol}")

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
    index_meta = DEFAULT_INDICES.get(index_symbol)
    index_name = index_meta["name"] if index_meta else index_symbol

    with get_connection(DbConfig(dsn=db_dsn)) as conn:
        repo = StockRepository(conn)
        repo.ensure_search_path()

        index_id = repo.upsert_security(security_type="INDEX", canonical_symbol=index_symbol, name=index_name)
        stooq_idx = index_meta["stooq"] if index_meta else None
        if stooq_idx:
            repo.upsert_security_identifier(security_id=index_id, provider="stooq", identifier=stooq_idx)

        securities_upserted = 0
        symbol_to_security_id: dict[str, int] = {}
        log.info("Upserting %d securities for index %s", len(companies), index_symbol)
        for i, (_, r) in enumerate(companies.iterrows()):
            canonical_symbol = str(r["symbol"]).strip().upper()
            security_name = str(r.get("security") or "").strip() or None
            log.info("[%d/%d] Upserting security: %s", i + 1, len(companies), canonical_symbol)
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

            repo.upsert_index_membership(
                index_id=index_id,
                security_id=security_id,
                as_of_date=as_of,
                date_first_added=_parse_optional_date(r.get("date_first_added")),
            )
            repo.upsert_security_detail(
                security_id=security_id,
                sector=str(r.get("gics_sector") or "").strip() or None,
                sub_industry=str(r.get("gics_sub_industry") or "").strip() or None,
                headquarters=str(r.get("headquarters") or "").strip() or None,
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

    log.info("Upserted %s securities and %s wiki summaries for %s", securities_upserted, wiki_upserted, index_symbol)
    return WikiSyncResult(securities_upserted=securities_upserted, wiki_upserted=wiki_upserted)


@dataclass(frozen=True)
class FundamentalsSyncResult:
    securities_scanned: int
    snapshots_upserted: int
    actions_upserted: int


def sync_index_fundamentals_to_db(
    *,
    db_dsn: str,
    index_symbol: str,
    symbols: Iterable[str] | None,
    limit: int | None,
    http_timeout_seconds: float,
    user_agent: str,
) -> FundamentalsSyncResult:
    snapshots_upserted = 0
    actions_upserted = 0
    securities_scanned = 0

    eodhd_api_token = os.getenv("EODHD_API_TOKEN") or None
    metadata_provider = (os.getenv("METADATA_PROVIDER") or "auto").strip().lower()
    eodhd_use_for_spx = (os.getenv("EODHD_USE_FOR_SPX") or "").strip().lower() in {"1", "true", "yes", "y", "on"}
    if metadata_provider not in {"auto", "yahoo", "eodhd"}:
        log.warning("Unknown METADATA_PROVIDER=%s, falling back to auto", metadata_provider)
        metadata_provider = "auto"
    if metadata_provider == "eodhd" and eodhd_api_token is None:
        log.warning("METADATA_PROVIDER=eodhd but EODHD_API_TOKEN is not set. Falling back to Yahoo.")
    log.info(
        "EODHD enabled: %s (index=%s, token_set=%s, spx_enabled=%s)",
        "METADATA_PROVIDER=%s (index=%s, token_set=%s, legacy_spx=%s)",
        metadata_provider,
        eodhd_api_token is not None,
        eodhd_use_for_spx,
    )

    with requests.Session() as http, get_connection(DbConfig(dsn=db_dsn)) as conn:
        repo = StockRepository(conn)
        repo = StockRepository(conn)
        repo.ensure_search_path()

        try:
            from collectors.yahoo_finance import warmup_yahoo_session

            if metadata_provider != "eodhd" or eodhd_api_token is None:
                warmup_yahoo_session(http, timeout=http_timeout_seconds, user_agent=user_agent)
        except Exception:
            pass

        constituents = repo.list_latest_index_constituents(index_symbol=index_symbol, symbols=symbols, limit=limit)
        if not constituents:
            sync_index_wiki_to_db(
                db_dsn=db_dsn,
                index_symbol=index_symbol,
                symbols=symbols,
                limit=limit,
                wiki_lang="zh",
                http_timeout_seconds=http_timeout_seconds,
                user_agent=user_agent,
            )
            constituents = repo.list_latest_index_constituents(index_symbol=index_symbol, symbols=symbols, limit=limit)

        today = datetime.utcnow().date()
        recent_cutoff = today - timedelta(days=370)
        log.info("Scanning fundamentals for %d constituents of %s", len(constituents), index_symbol)
        for i, c in enumerate(constituents):
            securities_scanned += 1
            is_hk = index_symbol in {"^HSI", "^HSTECH"}
            use_eodhd = False
            if metadata_provider == "eodhd":
                use_eodhd = eodhd_api_token is not None
            elif metadata_provider == "auto":
                use_eodhd = (is_hk or (index_symbol == "^SPX" and eodhd_use_for_spx)) and eodhd_api_token is not None
            yahoo_symbol = canonical_to_yahoo_symbol(canonical_symbol=c.canonical_symbol, index_symbol=index_symbol)
            log.info("[%d/%d] Processing %s (%s)", i + 1, len(constituents), c.canonical_symbol, yahoo_symbol)
            if use_eodhd:
                eodhd_symbol = canonical_to_eodhd_symbol(canonical_symbol=c.canonical_symbol, index_symbol=index_symbol)
                log.info("[%s] Using EODHD symbol %s", c.canonical_symbol, eodhd_symbol)
                repo.upsert_security_identifier(security_id=c.security_id, provider="eodhd", identifier=eodhd_symbol)
            elif not is_hk:
                repo.upsert_security_identifier(security_id=c.security_id, provider="yahoo", identifier=yahoo_symbol)

            try:
                if not repo.has_any_fundamental_snapshot(security_id=c.security_id, as_of_date=today):
                    try:
                        if use_eodhd:
                            eodhd_symbol = canonical_to_eodhd_symbol(canonical_symbol=c.canonical_symbol, index_symbol=index_symbol)
                            fund = fetch_eodhd_fundamentals(
                                http,
                                eodhd_symbol=eodhd_symbol,
                                api_token=eodhd_api_token,
                                timeout=http_timeout_seconds,
                                user_agent=user_agent,
                            )
                            currency = fund.currency or repo.get_security_currency(security_id=c.security_id) or ("HKD" if is_hk else "USD")
                            repo.upsert_fundamental_snapshot(
                                security_id=c.security_id,
                                as_of_date=today,
                                shares_outstanding=fund.shares_outstanding,
                                float_shares=None,
                                market_cap=fund.market_cap,
                                currency=currency,
                                source="eodhd",
                            )
                            snapshots_upserted += 1
                        else:
                            fund = fetch_yahoo_fundamentals(http, yahoo_symbol, timeout=http_timeout_seconds, user_agent=user_agent)
                            repo.upsert_fundamental_snapshot(
                                security_id=c.security_id,
                                as_of_date=today,
                                shares_outstanding=fund.shares_outstanding,
                                float_shares=fund.float_shares,
                                market_cap=fund.market_cap,
                                currency=fund.currency,
                                source="yahoo",
                            )
                            snapshots_upserted += 1
                    except Exception as e:
                        if use_eodhd:
                            log.warning("Failed fetching EODHD fundamentals for %s (%s): %s", c.canonical_symbol, c.stooq_symbol, e)
                            try:
                                fund_payload = fetch_stooq_fundamentals(
                                    http, c.stooq_symbol, timeout=http_timeout_seconds, user_agent=user_agent
                                )
                                shares_outstanding = fund_payload.get("shares_outstanding")
                                market_cap = fund_payload.get("market_cap")
                                currency = repo.get_security_currency(security_id=c.security_id) or "HKD"
                                repo.upsert_fundamental_snapshot(
                                    security_id=c.security_id,
                                    as_of_date=today,
                                    shares_outstanding=int(shares_outstanding) if shares_outstanding is not None else None,
                                    float_shares=None,
                                    market_cap=float(market_cap) if market_cap is not None else None,
                                    currency=currency,
                                    source="stooq",
                                )
                                snapshots_upserted += 1
                            except Exception as e2:
                                log.warning(
                                    "Failed fetching Stooq fundamentals fallback for %s (%s): %s",
                                    c.canonical_symbol,
                                    c.stooq_symbol,
                                    e2,
                                )
                        else:
                            log.warning("Failed fetching yahoo fundamentals for %s (%s): %s", c.canonical_symbol, yahoo_symbol, e)
                        if index_symbol == "^SPX":
                            cik = repo.get_security_cik(security_id=c.security_id)
                            if cik:
                                sec = fetch_sec_shares_outstanding(http, cik, timeout=http_timeout_seconds, user_agent=user_agent)
                                last_close = repo.get_latest_close(security_id=c.security_id, interval="1d")
                                market_cap = None
                                if last_close is not None:
                                    market_cap = float(sec.shares_outstanding) * float(last_close[1])
                                currency = repo.get_security_currency(security_id=c.security_id) or "USD"
                                repo.upsert_fundamental_snapshot(
                                    security_id=c.security_id,
                                    as_of_date=today,
                                    shares_outstanding=sec.shares_outstanding,
                                    float_shares=None,
                                    market_cap=market_cap,
                                    currency=currency,
                                    source="sec",
                                )
                                snapshots_upserted += 1
                else:
                    log.info("Skip fundamentals (already snapped today) for %s (%s)", c.canonical_symbol, yahoo_symbol)
            except Exception as e:
                log.warning("Failed syncing fundamentals for %s (%s): %s", c.canonical_symbol, yahoo_symbol, e)

            try:
                action_source = "eodhd" if use_eodhd else "yahoo"
                existing_count = repo.count_corporate_actions(security_id=c.security_id, source=action_source)
                max_ex_date = (
                    repo.get_latest_corporate_action_ex_date(security_id=c.security_id, source=action_source)
                    if existing_count > 0
                    else None
                )
                if existing_count > 0 and max_ex_date is not None and max_ex_date >= recent_cutoff:
                    log.info("Skip corporate actions (recent enough: %s) for %s (%s)", max_ex_date, c.canonical_symbol, yahoo_symbol)
                else:
                    if use_eodhd:
                        eodhd_symbol = canonical_to_eodhd_symbol(canonical_symbol=c.canonical_symbol, index_symbol=index_symbol)
                        from_date = "2000-01-01" if max_ex_date is None else (max_ex_date - timedelta(days=30)).isoformat()
                        to_date = today.isoformat()
                        currency_default = repo.get_security_currency(security_id=c.security_id) or ("HKD" if is_hk else "USD")

                        dividends = fetch_eodhd_dividends(
                            http,
                            eodhd_symbol=eodhd_symbol,
                            api_token=eodhd_api_token,
                            from_date=from_date,
                            to_date=to_date,
                            timeout=http_timeout_seconds,
                            user_agent=user_agent,
                        )
                        for d in dividends:
                            repo.upsert_corporate_action(
                                security_id=c.security_id,
                                ex_date=d.ex_date,
                                action_type="DIVIDEND",
                                cash_amount=d.cash_amount,
                                currency=d.currency or currency_default,
                                split_numerator=None,
                                split_denominator=None,
                                source="eodhd",
                                raw_payload=d.raw_payload,
                            )
                            actions_upserted += 1

                        splits = fetch_eodhd_splits(
                            http,
                            eodhd_symbol=eodhd_symbol,
                            api_token=eodhd_api_token,
                            from_date=from_date,
                            to_date=to_date,
                            timeout=http_timeout_seconds,
                            user_agent=user_agent,
                        )
                        for s in splits:
                            repo.upsert_corporate_action(
                                security_id=c.security_id,
                                ex_date=s.ex_date,
                                action_type="SPLIT",
                                cash_amount=None,
                                currency=None,
                                split_numerator=s.split_numerator,
                                split_denominator=s.split_denominator,
                                source="eodhd",
                                raw_payload=s.raw_payload,
                            )
                            actions_upserted += 1
                    else:
                        range_ = "max" if existing_count == 0 else "2y"
                        actions = fetch_yahoo_corporate_actions(
                            http,
                            yahoo_symbol,
                            timeout=http_timeout_seconds,
                            user_agent=user_agent,
                            range_=range_,
                        )
                        for a in actions:
                            repo.upsert_corporate_action(
                                security_id=c.security_id,
                                ex_date=a.ex_date,
                                action_type=a.action_type,
                                cash_amount=a.cash_amount,
                                currency=a.currency,
                                split_numerator=a.split_numerator,
                                split_denominator=a.split_denominator,
                                source="yahoo",
                                raw_payload=a.raw_payload,
                            )
                            actions_upserted += 1
            except Exception as e:
                log.warning("Failed fetching corporate actions for %s (%s): %s", c.canonical_symbol, yahoo_symbol, e)
            try:
                import random
                import time

                time.sleep(2.0 + random.random() * 1.2)
            except Exception:
                pass

    return FundamentalsSyncResult(
        securities_scanned=securities_scanned,
        snapshots_upserted=snapshots_upserted,
        actions_upserted=actions_upserted,
    )


def sync_sp500_wiki_to_db(
    *,
    db_dsn: str,
    symbols: Iterable[str] | None,
    limit: int | None,
    wiki_lang: str,
    http_timeout_seconds: float,
    user_agent: str,
) -> WikiSyncResult:
    return sync_index_wiki_to_db(
        db_dsn=db_dsn,
        index_symbol="^SPX",
        symbols=symbols,
        limit=limit,
        wiki_lang=wiki_lang,
        http_timeout_seconds=http_timeout_seconds,
        user_agent=user_agent,
    )


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


def _sync_single_security_prices(
    *,
    repo: StockRepository,
    http: requests.Session,
    security_id: int,
    canonical_symbol: str,
    stooq_symbol: str,
    start: date,
    end: date,
    interval: str,
    freq: str,
    http_timeout_seconds: float,
    user_agent: str,
    source: str = "stooq",
) -> int:
    """
    Syncs prices for a single security. Detects adjustments (splits/dividends)
    and re-syncs full history if necessary.
    """
    max_date = repo.get_max_bar_date(security_id=security_id, interval=interval)
    effective_start = start
    if max_date is not None:
        # Look back 7 days to catch any data updates/fixes from provider
        effective_start = max(start, max_date - timedelta(days=7))

    if effective_start > end:
        return 0

    bars_upserted = 0
    # Use a flag to avoid redundant re-syncs in the same call
    full_history_synced = False

    for chunk_start, chunk_end in _iter_date_ranges(effective_start, end, max_days=4000):
        if full_history_synced:
            break

        log.info("[%s] Fetching prices from %s to %s", canonical_symbol, chunk_start, chunk_end)
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

            if df.empty:
                log.info("[%s] No data found for this range", canonical_symbol)
                continue

            # Detect splits/dividends:
            if max_date is not None and not full_history_synced:
                overlap_df = df[df["date"].dt.date <= max_date]
                if not overlap_df.empty:
                    db_prices = repo.get_price_bars_map(
                        security_id=security_id,
                        interval=interval,
                        start_date=overlap_df["date"].min().date(),
                        end_date=overlap_df["date"].max().date(),
                    )

                    mismatch_found = False
                    for _, r in overlap_df.iterrows():
                        d = r["date"].date()
                        new_close = float(r["close"])
                        old_close = db_prices.get(d)
                        # If difference > 0.5%, assume corporate action (split/dividend)
                        if old_close is not None and abs(new_close - old_close) / old_close > 0.005:
                            log.info(
                                "Adjustment detected for %s at %s (old: %s, new: %s). Re-syncing full history.",
                                canonical_symbol,
                                d,
                                old_close,
                                new_close,
                            )
                            mismatch_found = True
                            break

                    if mismatch_found:
                        repo.delete_price_bars(security_id=security_id, interval=interval)
                        # Re-fetch the ENTIRE range from the absolute start
                        log.info("[%s] Resetting and fetching full history from %s", canonical_symbol, start)
                        df = fetch_prices_range(
                            session=http,
                            stooq_symbol=stooq_symbol,
                            start_date=start,
                            end_date=end,
                            freq=freq,
                            timeout=http_timeout_seconds,
                            user_agent=user_agent,
                            pause_seconds=0.1,
                        )
                        full_history_synced = True
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

        if not df.empty:
            rows = _df_to_price_rows(df, security_id=security_id, interval=interval, source=source)
            count = repo.upsert_price_bars(rows)
            bars_upserted += count
            log.info("[%s] Upserted %d bars", canonical_symbol, count)

    return bars_upserted


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

            bars_upserted += _sync_single_security_prices(
                repo=repo,
                http=http,
                security_id=security_id,
                canonical_symbol=canonical_symbol,
                stooq_symbol=stooq_symbol,
                start=start,
                end=end,
                interval=interval,
                freq=freq,
                http_timeout_seconds=http_timeout_seconds,
                user_agent=user_agent,
            )

    return PriceSyncResult(securities_scanned=securities_scanned, bars_upserted=bars_upserted)


def sync_index_prices_to_db(
    *,
    db_dsn: str,
    index_symbol: str,
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

        constituents = repo.list_latest_index_constituents(index_symbol=index_symbol, symbols=symbols, limit=limit)
        if not constituents:
            # Fallback to wiki sync if no constituents in DB
            sync_index_wiki_to_db(
                db_dsn=db_dsn,
                index_symbol=index_symbol,
                symbols=symbols,
                limit=limit,
                wiki_lang="zh",
                http_timeout_seconds=http_timeout_seconds,
                user_agent=user_agent,
            )
            constituents = repo.list_latest_index_constituents(index_symbol=index_symbol, symbols=symbols, limit=limit)

        for c in constituents:
            securities_scanned += 1
            bars_upserted += _sync_single_security_prices(
                repo=repo,
                http=http,
                security_id=c.security_id,
                canonical_symbol=c.canonical_symbol,
                stooq_symbol=c.stooq_symbol,
                start=start,
                end=end,
                interval=interval,
                freq=freq,
                http_timeout_seconds=http_timeout_seconds,
                user_agent=user_agent,
            )

    if include_indices:
        # Also sync the index price itself
        idx_meta = DEFAULT_INDICES.get(index_symbol)
        if idx_meta:
            with requests.Session() as http, get_connection(DbConfig(dsn=db_dsn)) as conn:
                repo = StockRepository(conn)
                repo.ensure_search_path()
                security_id = repo.upsert_security(
                    security_type=idx_meta["type"],
                    canonical_symbol=index_symbol,
                    name=idx_meta["name"],
                )
                stooq_idx = idx_meta["stooq"]
                repo.upsert_security_identifier(security_id=security_id, provider="stooq", identifier=stooq_idx)

                bars_upserted += _sync_single_security_prices(
                    repo=repo,
                    http=http,
                    security_id=security_id,
                    canonical_symbol=index_symbol,
                    stooq_symbol=stooq_idx,
                    start=start,
                    end=end,
                    interval=interval,
                    freq=freq,
                    http_timeout_seconds=http_timeout_seconds,
                    user_agent=user_agent,
                )
                securities_scanned += 1

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
    return sync_index_prices_to_db(
        db_dsn=db_dsn,
        index_symbol="^SPX",
        start_date=start_date,
        end_date=end_date,
        interval=interval,
        symbols=symbols,
        limit=limit,
        http_timeout_seconds=http_timeout_seconds,
        user_agent=user_agent,
        include_indices=include_indices,
    )


def sync_index_daily_incremental(
    *,
    db_dsn: str,
    index_symbol: str,
    interval: str = "1d",
    symbols: Iterable[str] | None = None,
    limit: int | None = None,
    http_timeout_seconds: float,
    user_agent: str,
    include_indices: bool = True,
) -> PriceSyncResult:
    # HSI usually has missing volume on the most recent day in Stooq, 
    # so we default to T-2 for HSI to ensure data quality.
    days_ago = 2 if index_symbol.upper() == "^HSI" else 1
    end_date = (datetime.utcnow() - timedelta(days=days_ago)).strftime("%Y-%m-%d")
    
    start_floor = "1900-01-01"
    return sync_index_prices_to_db(
        db_dsn=db_dsn,
        index_symbol=index_symbol,
        start_date=start_floor,
        end_date=end_date,
        interval=interval,
        symbols=symbols,
        limit=limit,
        http_timeout_seconds=http_timeout_seconds,
        user_agent=user_agent,
        include_indices=include_indices,
    )


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
    return sync_index_daily_incremental(
        db_dsn=db_dsn,
        index_symbol="^SPX",
        interval=interval,
        symbols=symbols,
        limit=limit,
        http_timeout_seconds=http_timeout_seconds,
        user_agent=user_agent,
        include_indices=include_indices,
    )
