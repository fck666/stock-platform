"""
Stooq Price Collector

This module handles fetching historical price data from Stooq.com.
Stooq provides free CSV downloads for global equity data.

Key Features:
- Downloads daily OHLCV data in CSV format.
- Supports both single-symbol fetch and bulk fetching.
- Implements robust retry logic with exponential backoff to handle rate limits and network errors.
- Parses CSV response into pandas DataFrames.
"""
from __future__ import annotations

import io
import logging
import time
from dataclasses import dataclass
from datetime import date
from typing import Iterable
from urllib.parse import quote

import pandas as pd
import requests


log = logging.getLogger(__name__)


@dataclass(frozen=True)
class PriceQuery:
    """
    Represents a query for fetching price data.
    """
    stooq_symbol: str
    start_date: date | None = None
    end_date: date | None = None


def _build_url(stooq_symbol: str) -> str:
    """
    Builds the Stooq download URL for a given symbol.
    Format: https://stooq.com/q/d/l/?s=<SYMBOL>&i=d
    """
    return f"https://stooq.com/q/d/l/?s={quote(stooq_symbol)}&i=d"


def fetch_daily_prices(
    session: requests.Session,
    query: PriceQuery,
    timeout: float,
    user_agent: str,
) -> pd.DataFrame:
    """
    Fetch daily prices for a single symbol.
    
    Args:
        session: The requests session to use.
        query: The price query containing symbol and optional dates.
        timeout: Request timeout in seconds.
        user_agent: User agent string to avoid blocking.

    Returns:
        pd.DataFrame: A DataFrame with columns [date, open, high, low, close, volume, symbol].
                      Returns an empty DataFrame if no data is found but request succeeds.
    
    Raises:
        RuntimeError: If the response is invalid or empty.
    """
    url = _build_url(query.stooq_symbol)
    headers = {"User-Agent": user_agent}
    resp = session.get(url, headers=headers, timeout=timeout)
    resp.raise_for_status()

    text = resp.text.strip()
    if text == "" or "Date,Open,High,Low,Close,Volume" not in text:
        raise RuntimeError(f"No data for {query.stooq_symbol} from stooq")

    df = pd.read_csv(io.StringIO(text), parse_dates=["Date"])
    df = df.rename(columns=str.lower)
    df = df.sort_values("date").reset_index(drop=True)
    df["symbol"] = query.stooq_symbol

    if query.start_date is not None:
        df = df[df["date"].dt.date >= query.start_date].copy()
    if query.end_date is not None:
        df = df[df["date"].dt.date <= query.end_date].copy()

    return df.reset_index(drop=True)


def fetch_many_daily_prices(
    session: requests.Session,
    queries: Iterable[PriceQuery],
    timeout: float,
    user_agent: str,
) -> dict[str, pd.DataFrame]:
    """
    Fetch prices for multiple symbols in sequence.
    Errors are logged but do not stop the process (best-effort).
    """
    out: dict[str, pd.DataFrame] = {}
    for q in queries:
        try:
            out[q.stooq_symbol] = fetch_daily_prices(session, q, timeout=timeout, user_agent=user_agent)
            log.info("Fetched %s rows for %s", len(out[q.stooq_symbol]), q.stooq_symbol)
        except Exception as e:
            log.warning("Failed fetching %s: %s", q.stooq_symbol, e)
    return out


def fetch_prices_range(
    *,
    session: requests.Session,
    stooq_symbol: str,
    start_date: date,
    end_date: date,
    freq: str = "d",
    timeout: float = 30,
    user_agent: str = "stock-platform-data-collector/0.1",
    max_attempts: int = 5,
    pause_seconds: float = 0.1,
) -> pd.DataFrame:
    """
    Robustly fetch price history with retry logic and backoff.
    
    Args:
        stooq_symbol: The symbol on Stooq (e.g. 'AAPL.US')
        start_date: Start date (inclusive)
        end_date: End date (inclusive)
        freq: Frequency ('d' for daily)
        max_attempts: Number of retries on failure (default: 5)
        pause_seconds: Sleep time before request (to be nice to the server)
    
    Returns:
        pd.DataFrame: DataFrame with historical prices.
    
    Raises:
        RuntimeError: If all attempts fail.
    """
    url = "https://stooq.com/q/d/l/"
    params = {
        "s": stooq_symbol,
        "d1": start_date.strftime("%Y%m%d"),
        "d2": end_date.strftime("%Y%m%d"),
        "i": freq,
    }
    headers = {"User-Agent": user_agent}

    last_error: Exception | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            if pause_seconds > 0:
                time.sleep(pause_seconds)
            resp = session.get(url, params=params, headers=headers, timeout=timeout)
            if resp.status_code in {429, 502, 503, 504}:
                raise requests.HTTPError(f"HTTP {resp.status_code} from stooq", response=resp)
            resp.raise_for_status()
            text = resp.text.strip()
            if "Exceeded the daily hits limit" in text:
                raise RuntimeError("Stooq rate limit exceeded (daily hits limit).")
            if text == "" or "Date,Open,High,Low,Close" not in text:
                snippet = text[:200].replace("\n", "\\n")
                raise RuntimeError(f"No data for {stooq_symbol} from stooq (resp: {snippet})")

            df = pd.read_csv(io.StringIO(text), parse_dates=["Date"])
            df = df.rename(columns=str.lower)
            df = df.sort_values("date").reset_index(drop=True)
            df["symbol"] = stooq_symbol
            return df.reset_index(drop=True)
        except (requests.exceptions.SSLError, requests.exceptions.ConnectionError, requests.exceptions.Timeout) as e:
            last_error = e
        except requests.HTTPError as e:
            last_error = e
        except Exception:
            raise

        backoff = min(20.0, 0.5 * (2 ** (attempt - 1)))
        time.sleep(backoff)

    raise RuntimeError(f"Failed fetching {stooq_symbol} after {max_attempts} attempts: {last_error}")
