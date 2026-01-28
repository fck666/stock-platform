import logging
import random
import time
from dataclasses import dataclass
from datetime import datetime, timezone, date

import requests

log = logging.getLogger(__name__)

_WARMED_UP = False


def _sleep_with_jitter(base_seconds: float) -> None:
    time.sleep(base_seconds + random.random() * base_seconds)


def warmup_yahoo_session(session: requests.Session, *, timeout: float, user_agent: str) -> None:
    global _WARMED_UP
    if _WARMED_UP:
        return
    headers = {
        "User-Agent": user_agent,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9,zh-CN;q=0.8",
    }
    try:
        session.get("https://finance.yahoo.com/", headers=headers, timeout=timeout)
        _WARMED_UP = True
    except Exception:
        return

def _request_json(
    session: requests.Session,
    url: str,
    *,
    timeout: float,
    user_agent: str,
    max_retries: int = 4,
) -> dict:
    headers = {
        "User-Agent": user_agent,
        "Accept": "application/json,text/plain,*/*",
        "Accept-Language": "en-US,en;q=0.9,zh-CN;q=0.8",
        "Referer": "https://finance.yahoo.com/",
    }

    last_err: Exception | None = None
    for attempt in range(max_retries + 1):
        try:
            resp = session.get(url, headers=headers, timeout=timeout)
            if resp.status_code == 401:
                warmup_yahoo_session(session, timeout=timeout, user_agent=user_agent)
                raise RuntimeError(f"HTTP 401 for {url}")
            if resp.status_code == 429:
                retry_after = resp.headers.get("Retry-After")
                if retry_after and retry_after.isdigit():
                    _sleep_with_jitter(float(retry_after))
                else:
                    _sleep_with_jitter(10.0 * (attempt + 1))
                raise RuntimeError(f"HTTP 429 for {url}")
            if resp.status_code in (500, 502, 503, 504):
                raise RuntimeError(f"HTTP {resp.status_code} for {url}")
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            last_err = e
            if attempt >= max_retries:
                break
            _sleep_with_jitter(1.5 * (2**attempt))
    raise last_err or RuntimeError(f"Failed requesting {url}")


def canonical_to_yahoo_symbol(*, canonical_symbol: str, index_symbol: str | None = None) -> str:
    s = canonical_symbol.strip().upper()
    if index_symbol and index_symbol.upper() in {"^HSI", "^HSTECH"}:
        digits = "".join([c for c in s if c.isdigit()])
        if digits:
            n = int(digits)
            return f"{n:04d}.HK"
        return s
    if s.count(".") == 1 and len(s) <= 6:
        s = s.replace(".", "-")
    return s


@dataclass(frozen=True)
class YahooFundamentals:
    market_cap: int | None
    shares_outstanding: int | None
    float_shares: int | None
    currency: str | None
    raw_payload: dict


def fetch_yahoo_fundamentals(
    session: requests.Session,
    yahoo_symbol: str,
    *,
    timeout: float,
    user_agent: str,
) -> YahooFundamentals:
    url = (
        f"https://query2.finance.yahoo.com/v10/finance/quoteSummary/{yahoo_symbol}"
        "?modules=price,defaultKeyStatistics"
    )
    payload = _request_json(session, url, timeout=timeout, user_agent=user_agent)
    result = (payload.get("quoteSummary") or {}).get("result") or []
    if not result:
        raise RuntimeError(f"No quoteSummary result for {yahoo_symbol}")
    r0 = result[0]
    price = r0.get("price") or {}
    stats = r0.get("defaultKeyStatistics") or {}

    def _raw_int(x) -> int | None:
        if isinstance(x, dict) and "raw" in x:
            try:
                return int(x["raw"])
            except Exception:
                return None
        return None

    market_cap = _raw_int(price.get("marketCap"))
    currency = price.get("currency")
    shares_outstanding = _raw_int(stats.get("sharesOutstanding"))
    float_shares = _raw_int(stats.get("floatShares"))
    return YahooFundamentals(
        market_cap=market_cap,
        shares_outstanding=shares_outstanding,
        float_shares=float_shares,
        currency=currency,
        raw_payload=payload,
    )


@dataclass(frozen=True)
class YahooCorporateAction:
    ex_date: date
    action_type: str
    cash_amount: float | None
    currency: str | None
    split_numerator: int | None
    split_denominator: int | None
    raw_payload: dict


def fetch_yahoo_corporate_actions(
    session: requests.Session,
    yahoo_symbol: str,
    *,
    timeout: float,
    user_agent: str,
) -> list[YahooCorporateAction]:
    url = (
        f"https://query1.finance.yahoo.com/v8/finance/chart/{yahoo_symbol}"
        "?range=max&interval=1d&events=div%2Csplits"
    )
    payload = _request_json(session, url, timeout=timeout, user_agent=user_agent)
    chart = payload.get("chart") or {}
    result = chart.get("result") or []
    if not result:
        return []
    r0 = result[0]
    meta = r0.get("meta") or {}
    currency = meta.get("currency")
    events = r0.get("events") or {}

    out: list[YahooCorporateAction] = []

    dividends = events.get("dividends") or {}
    for _, d in dividends.items():
        ts = d.get("date")
        if ts is None:
            continue
        ex_date = datetime.fromtimestamp(int(ts), tz=timezone.utc).date()
        amount = d.get("amount")
        cash_amount = float(amount) if amount is not None else None
        out.append(
            YahooCorporateAction(
                ex_date=ex_date,
                action_type="DIVIDEND",
                cash_amount=cash_amount,
                currency=currency,
                split_numerator=None,
                split_denominator=None,
                raw_payload=d,
            )
        )

    splits = events.get("splits") or {}
    for _, s in splits.items():
        ts = s.get("date")
        if ts is None:
            continue
        ex_date = datetime.fromtimestamp(int(ts), tz=timezone.utc).date()
        num = s.get("numerator")
        den = s.get("denominator")
        split_numerator = int(num) if num is not None else None
        split_denominator = int(den) if den is not None else None
        out.append(
            YahooCorporateAction(
                ex_date=ex_date,
                action_type="SPLIT",
                cash_amount=None,
                currency=currency,
                split_numerator=split_numerator,
                split_denominator=split_denominator,
                raw_payload=s,
            )
        )

    out.sort(key=lambda x: x.ex_date, reverse=True)
    return out
