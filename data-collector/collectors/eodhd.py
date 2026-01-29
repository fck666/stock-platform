from __future__ import annotations

import logging
import re
import time
from dataclasses import dataclass
from datetime import date

import requests

log = logging.getLogger(__name__)

_LAST_REQUEST_AT: float | None = None


def _get_env_float(name: str, default: float) -> float:
    try:
        v = float((__import__("os").getenv(name) or "").strip())
        if v <= 0:
            return default
        return v
    except Exception:
        return default


def _get_env_int(name: str, default: int) -> int:
    try:
        v = int((__import__("os").getenv(name) or "").strip())
        if v < 0:
            return default
        return v
    except Exception:
        return default


def _throttle() -> None:
    global _LAST_REQUEST_AT
    rpm = _get_env_float("EODHD_MAX_RPM", 30.0)
    min_interval = 60.0 / rpm if rpm > 0 else 0.0
    if min_interval <= 0:
        return
    now = time.time()
    if _LAST_REQUEST_AT is None:
        _LAST_REQUEST_AT = now
        return
    wait = (_LAST_REQUEST_AT + min_interval) - now
    if wait > 0:
        time.sleep(wait)
    _LAST_REQUEST_AT = time.time()


def _sleep_backoff(attempt: int) -> None:
    base = _get_env_float("EODHD_BACKOFF_BASE_SECONDS", 1.0)
    cap = _get_env_float("EODHD_BACKOFF_MAX_SECONDS", 30.0)
    delay = min(cap, base * (2**attempt))
    time.sleep(delay)


def canonical_to_eodhd_symbol(*, canonical_symbol: str, index_symbol: str | None = None) -> str:
    s = canonical_symbol.strip().upper()
    if index_symbol and (index_symbol.upper() in {"^HSI", "^HSTECH"} or index_symbol.upper().startswith("^HK")):
        digits = "".join([c for c in s if c.isdigit()])
        if digits:
            n = int(digits)
            return f"{n:04d}.HK"
        return f"{s}.HK" if "." not in s else s
    if index_symbol and index_symbol.upper() == "^SPX":
        if s.count(".") == 1 and len(s) <= 6:
            s = s.replace(".", "-")
        return f"{s}.US"
    if s.count(".") == 1 and len(s) <= 6:
        s = s.replace(".", "-")
    return s


def _request_json(
    session: requests.Session,
    url: str,
    *,
    timeout: float,
    user_agent: str,
) -> object:
    headers = {
        "User-Agent": user_agent,
        "Accept": "application/json,text/plain,*/*",
    }
    max_retries = _get_env_int("EODHD_MAX_RETRIES", 4)
    last_err: Exception | None = None
    for attempt in range(max_retries + 1):
        try:
            _throttle()
            resp = session.get(url, headers=headers, timeout=timeout)
            if resp.status_code == 403:
                msg = (resp.text or "").strip()
                raise RuntimeError(f"EODHD 403 for {url}: {msg}")
            if resp.status_code == 429:
                retry_after = resp.headers.get("Retry-After")
                if retry_after and retry_after.strip().isdigit():
                    time.sleep(float(retry_after.strip()))
                else:
                    _sleep_backoff(attempt)
                continue
            if resp.status_code in (500, 502, 503, 504):
                _sleep_backoff(attempt)
                continue
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            last_err = e
            if attempt >= max_retries:
                break
            _sleep_backoff(attempt)
    raise last_err or RuntimeError(f"Failed requesting {url}")


@dataclass(frozen=True)
class EodhdDividend:
    ex_date: date
    cash_amount: float | None
    currency: str | None
    raw_payload: dict


@dataclass(frozen=True)
class EodhdSplit:
    ex_date: date
    split_numerator: int | None
    split_denominator: int | None
    raw_payload: dict


@dataclass(frozen=True)
class EodhdFundamentals:
    market_cap: float | None
    shares_outstanding: int | None
    currency: str | None
    raw_payload: dict


def fetch_eodhd_dividends(
    session: requests.Session,
    *,
    eodhd_symbol: str,
    api_token: str,
    from_date: str | None,
    to_date: str | None,
    timeout: float,
    user_agent: str,
) -> list[EodhdDividend]:
    params = [f"api_token={api_token}", "fmt=json"]
    if from_date:
        params.append(f"from={from_date}")
    if to_date:
        params.append(f"to={to_date}")
    url = f"https://eodhd.com/api/div/{eodhd_symbol}?" + "&".join(params)
    payload = _request_json(session, url, timeout=timeout, user_agent=user_agent)

    if isinstance(payload, dict) and payload.get("code") and payload.get("message"):
        raise RuntimeError(f"EODHD dividends error for {eodhd_symbol}: {payload}")
    if not isinstance(payload, list):
        raise RuntimeError(f"Unexpected EODHD dividends payload for {eodhd_symbol}: {type(payload)}")

    out: list[EodhdDividend] = []
    for item in payload:
        if not isinstance(item, dict):
            continue
        d = item.get("date") or item.get("exDate") or item.get("ex_date")
        if not d:
            continue
        try:
            ex_date = date.fromisoformat(str(d)[:10])
        except Exception:
            continue

        v = item.get("value") if "value" in item else item.get("dividend") if "dividend" in item else item.get("amount")
        cash_amount: float | None = None
        if v is not None:
            try:
                cash_amount = float(v)
            except Exception:
                cash_amount = None

        currency = item.get("currency")
        out.append(EodhdDividend(ex_date=ex_date, cash_amount=cash_amount, currency=currency, raw_payload=item))

    out.sort(key=lambda x: x.ex_date, reverse=True)
    return out


def _parse_split_ratio(value: object) -> tuple[int | None, int | None]:
    if value is None:
        return (None, None)
    if isinstance(value, (int, float)):
        try:
            v = float(value)
        except Exception:
            return (None, None)
        if v <= 0:
            return (None, None)
        n = int(round(v))
        return (n, 1)
    s = str(value).strip()
    m = re.match(r"^\s*(\d+(?:\.\d+)?)\s*[:/]\s*(\d+(?:\.\d+)?)\s*$", s)
    if m:
        try:
            return (int(round(float(m.group(1)))), int(round(float(m.group(2)))))
        except Exception:
            return (None, None)
    m = re.match(r"^\s*(\d+(?:\.\d+)?)\s*[-xX]\s*(\d+(?:\.\d+)?)\s*$", s)
    if m:
        try:
            a = float(m.group(1))
            b = float(m.group(2))
            if a > 0 and b > 0:
                return (int(round(a)), int(round(b)))
        except Exception:
            return (None, None)
    return (None, None)


def fetch_eodhd_splits(
    session: requests.Session,
    *,
    eodhd_symbol: str,
    api_token: str,
    from_date: str | None,
    to_date: str | None,
    timeout: float,
    user_agent: str,
) -> list[EodhdSplit]:
    params = [f"api_token={api_token}", "fmt=json"]
    if from_date:
        params.append(f"from={from_date}")
    if to_date:
        params.append(f"to={to_date}")
    url = f"https://eodhd.com/api/splits/{eodhd_symbol}?" + "&".join(params)
    payload = _request_json(session, url, timeout=timeout, user_agent=user_agent)

    if isinstance(payload, dict) and payload.get("code") and payload.get("message"):
        raise RuntimeError(f"EODHD splits error for {eodhd_symbol}: {payload}")
    if not isinstance(payload, list):
        raise RuntimeError(f"Unexpected EODHD splits payload for {eodhd_symbol}: {type(payload)}")

    out: list[EodhdSplit] = []
    for item in payload:
        if not isinstance(item, dict):
            continue
        d = item.get("date") or item.get("exDate") or item.get("ex_date")
        if not d:
            continue
        try:
            ex_date = date.fromisoformat(str(d)[:10])
        except Exception:
            continue

        ratio = item.get("split") if "split" in item else item.get("ratio") if "ratio" in item else item.get("value")
        num, den = _parse_split_ratio(ratio)
        out.append(EodhdSplit(ex_date=ex_date, split_numerator=num, split_denominator=den, raw_payload=item))

    out.sort(key=lambda x: x.ex_date, reverse=True)
    return out


def fetch_eodhd_fundamentals(
    session: requests.Session,
    *,
    eodhd_symbol: str,
    api_token: str,
    timeout: float,
    user_agent: str,
) -> EodhdFundamentals:
    url = f"https://eodhd.com/api/fundamentals/{eodhd_symbol}?api_token={api_token}&fmt=json"
    payload_obj = _request_json(session, url, timeout=timeout, user_agent=user_agent)
    if not isinstance(payload_obj, dict):
        raise RuntimeError(f"Unexpected EODHD fundamentals payload for {eodhd_symbol}: {type(payload_obj)}")

    general = payload_obj.get("General") or {}
    highlights = payload_obj.get("Highlights") or {}

    market_cap: float | None = None
    for key in ("MarketCapitalization", "MarketCapitalizationMln", "market_cap"):
        if key in highlights and highlights.get(key) is not None:
            try:
                market_cap = float(highlights.get(key))
                if key == "MarketCapitalizationMln":
                    market_cap *= 1_000_000.0
                break
            except Exception:
                market_cap = None

    shares_outstanding: int | None = None
    if highlights.get("SharesOutstanding") is not None:
        try:
            shares_outstanding = int(float(highlights.get("SharesOutstanding")))
        except Exception:
            shares_outstanding = None

    currency = general.get("CurrencyCode") or highlights.get("Currency") or payload_obj.get("CurrencyCode")
    return EodhdFundamentals(
        market_cap=market_cap,
        shares_outstanding=shares_outstanding,
        currency=currency,
        raw_payload=payload_obj,
    )
