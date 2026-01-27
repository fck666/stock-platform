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
    stooq_symbol: str
    start_date: date | None = None
    end_date: date | None = None


def _build_url(stooq_symbol: str) -> str:
    return f"https://stooq.com/q/d/l/?s={quote(stooq_symbol)}&i=d"


def fetch_daily_prices(
    session: requests.Session,
    query: PriceQuery,
    timeout: float,
    user_agent: str,
) -> pd.DataFrame:
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
