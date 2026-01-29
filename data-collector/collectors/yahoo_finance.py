import logging
import random
import time
from dataclasses import dataclass
from datetime import datetime, timezone, date

import requests
import yfinance as yf

log = logging.getLogger(__name__)

_WARMED_UP = False
_CRUMB: str | None = None
_CRUMB_AT: float | None = None


def _sleep_with_jitter(base_seconds: float) -> None:
    time.sleep(base_seconds + random.random() * base_seconds)


def warmup_yahoo_session(session: requests.Session, *, timeout: float, user_agent: str) -> None:
    global _WARMED_UP
    if _WARMED_UP:
        return
    
    # Updated warmup strategy: visit fc.yahoo.com first to get the initial cookie
    headers = {
        "User-Agent": user_agent,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language": "en-US,en;q=0.9",
        "DNT": "1",
        "Upgrade-Insecure-Requests": "1",
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none",
        "Sec-Fetch-User": "?1",
    }
    try:
        # Step 1: Visit fc.yahoo.com to get a fresh cookie
        log.info("Warming up Yahoo session via fc.yahoo.com")
        session.get("https://fc.yahoo.com", headers=headers, timeout=timeout, allow_redirects=True)
        
        # Step 2: Visit the main finance page
        session.get("https://finance.yahoo.com/", headers=headers, timeout=timeout, allow_redirects=True)
        
        # Step 3: Get crumb
        _refresh_crumb(session, timeout=timeout, user_agent=user_agent)
        _WARMED_UP = True
    except Exception as e:
        log.warning("Yahoo session warmup failed: %s", e)
        return


def _refresh_crumb(session: requests.Session, *, timeout: float, user_agent: str) -> None:
    global _CRUMB, _CRUMB_AT
    headers = {
        "User-Agent": user_agent,
        "Accept": "*/*",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": "https://finance.yahoo.com/",
        "Origin": "https://finance.yahoo.com",
    }
    try:
        # Try multiple crumb endpoints
        endpoints = [
            "https://query2.finance.yahoo.com/v1/test/getcrumb",
            "https://query1.finance.yahoo.com/v1/test/getcrumb"
        ]
        for url in endpoints:
            resp = session.get(url, headers=headers, timeout=timeout)
            if resp.status_code == 200:
                crumb = (resp.text or "").strip()
                # Yahoo sometimes returns 'Too Many Requests' as the text instead of a real crumb
                if crumb and len(crumb) < 20 and "Request" not in crumb:
                    _CRUMB = crumb
                    _CRUMB_AT = time.time()
                    log.info("Successfully refreshed Yahoo crumb: %s", _CRUMB)
                    return
                else:
                    log.warning("Received invalid crumb text: %s", crumb)
        log.warning("Failed to refresh Yahoo crumb from all endpoints")
    except Exception as e:
        log.warning("Error refreshing Yahoo crumb: %s", e)
        return


def _maybe_add_crumb(url: str) -> str:
    if _CRUMB is None:
        return url
    if "crumb=" in url:
        return url
    sep = "&" if "?" in url else "?"
    return f"{url}{sep}crumb={_CRUMB}"


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
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": "https://finance.yahoo.com/",
    }

    last_err: Exception | None = None
    for attempt in range(max_retries + 1):
        try:
            # Only add crumb if we actually have one
            effective_url = _maybe_add_crumb(url) if ("quoteSummary" in url or "finance/quote" in url) else url
            resp = session.get(effective_url, headers=headers, timeout=timeout)
            
            if resp.status_code == 401:
                log.info("Yahoo 401 detected, re-warming session...")
                global _WARMED_UP
                _WARMED_UP = False
                warmup_yahoo_session(session, timeout=timeout, user_agent=user_agent)
                # Retry with new session
                continue
                
            if resp.status_code == 429:
                log.warning("Yahoo 429 (Rate Limit) for %s. Attempt %d/%d", url, attempt + 1, max_retries + 1)
                retry_after = resp.headers.get("Retry-After")
                if retry_after and retry_after.isdigit():
                    _sleep_with_jitter(float(retry_after))
                else:
                    _sleep_with_jitter(10.0 * (attempt + 1))
                continue

            if resp.status_code in (500, 502, 503, 504):
                log.warning("Yahoo %d for %s. Attempt %d", resp.status_code, url, attempt + 1)
                _sleep_with_jitter(2.0 * (attempt + 1))
                continue

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
    if index_symbol and (index_symbol.upper() in {"^HSI", "^HSTECH"} or index_symbol.upper().startswith("^HK")):
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
    """
    Fetches fundamentals. Attempts yfinance first as it is more robust, 
    then falls back to manual requests.
    """
    # Attempt 1: yfinance (it handles session/crumb complexity internally)
    try:
        log.info("[%s] Attempting yfinance for fundamentals", yahoo_symbol)
        ticker = yf.Ticker(yahoo_symbol)
        
        # yfinance info can be slow or blocked too, but it has internal retries
        info = ticker.info
        if info and "symbol" in info:
            market_cap = info.get("marketCap")
            shares_outstanding = info.get("sharesOutstanding")
            float_shares = info.get("floatShares")
            currency = info.get("currency")
            
            if market_cap or shares_outstanding:
                return YahooFundamentals(
                    market_cap=market_cap,
                    shares_outstanding=shares_outstanding,
                    float_shares=float_shares,
                    currency=currency,
                    raw_payload=info,
                )
    except Exception as e:
        log.warning("[%s] yfinance failed: %s. Falling back to manual requests.", yahoo_symbol, e)

    # Attempt 2: Manual quoteSummary
    warmup_yahoo_session(session, timeout=timeout, user_agent=user_agent)

    quote_summary_url = (
        f"https://query2.finance.yahoo.com/v10/finance/quoteSummary/{yahoo_symbol}"
        "?modules=price,defaultKeyStatistics"
    )

    def _raw_int(x) -> int | None:
        if isinstance(x, dict) and "raw" in x:
            try:
                return int(x["raw"])
            except Exception:
                return None
        if isinstance(x, (int, float)):
            try:
                return int(x)
            except Exception:
                return None
        return None

    try:
        payload = _request_json(session, quote_summary_url, timeout=timeout, user_agent=user_agent)
        result = (payload.get("quoteSummary") or {}).get("result") or []
        if not result:
            raise RuntimeError(f"No quoteSummary result for {yahoo_symbol}")
        r0 = result[0]
        price = r0.get("price") or {}
        stats = r0.get("defaultKeyStatistics") or {}
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
    except Exception as e:
        log.warning("[%s] quoteSummary failed: %s", yahoo_symbol, e)

    # Attempt 3: Manual quote (v7)
    quote_url = f"https://query1.finance.yahoo.com/v7/finance/quote?symbols={yahoo_symbol}"
    payload = _request_json(session, quote_url, timeout=timeout, user_agent=user_agent)
    result = (payload.get("quoteResponse") or {}).get("result") or []
    if not result:
        raise RuntimeError(f"No quote result for {yahoo_symbol}")
    r0 = result[0]
    market_cap = _raw_int(r0.get("marketCap"))
    shares_outstanding = _raw_int(r0.get("sharesOutstanding"))
    float_shares = _raw_int(r0.get("floatShares"))
    currency = r0.get("currency")
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
    range_: str = "max",
) -> list[YahooCorporateAction]:
    """
    Fetches corporate actions (dividends, splits).
    Attempts yfinance first, then falls back to manual chart requests.
    """
    # Attempt 1: yfinance
    try:
        log.info("[%s] Attempting yfinance for corporate actions", yahoo_symbol)
        ticker = yf.Ticker(yahoo_symbol)
        
        # Fetch actions
        actions = ticker.actions
        out: list[YahooCorporateAction] = []
        
        if actions is not None and not actions.empty:
            for idx, row in actions.iterrows():
                ex_date = idx.date()
                div = row.get("Dividends")
                split = row.get("Stock Splits")
                
                if div and div > 0:
                    out.append(YahooCorporateAction(
                        ex_date=ex_date,
                        action_type="DIVIDEND",
                        cash_amount=float(div),
                        currency=None, # yfinance actions don't always have currency here
                        split_numerator=None,
                        split_denominator=None,
                        raw_payload=row.to_dict(),
                    ))
                
                if split and split > 0:
                    # yfinance split is usually 2.0 for 2-for-1
                    # We need numerator/denominator
                    # Often it's just a float, so we approximate
                    out.append(YahooCorporateAction(
                        ex_date=ex_date,
                        action_type="SPLIT",
                        cash_amount=None,
                        currency=None,
                        split_numerator=int(split) if split >= 1 else 1,
                        split_denominator=1 if split >= 1 else int(1/split),
                        raw_payload=row.to_dict(),
                    ))
            
            if out:
                out.sort(key=lambda x: x.ex_date, reverse=True)
                return out
    except Exception as e:
        log.warning("[%s] yfinance actions failed: %s. Falling back to manual chart requests.", yahoo_symbol, e)

    # Attempt 2: Manual chart endpoint
    url = (
        f"https://query1.finance.yahoo.com/v8/finance/chart/{yahoo_symbol}"
        f"?range={range_}&interval=1d&events=div%2Csplits"
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
