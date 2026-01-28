import logging
from dataclasses import dataclass
from datetime import date

import requests

from utils.date_utils import parse_ymd

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class SecSharesOutstanding:
    shares_outstanding: int
    as_of_date: date
    raw_payload: dict


def fetch_sec_shares_outstanding(
    session: requests.Session,
    cik: str,
    *,
    timeout: float,
    user_agent: str,
) -> SecSharesOutstanding:
    digits = "".join([c for c in str(cik) if c.isdigit()])
    if not digits:
        raise RuntimeError("CIK is empty")
    cik10 = digits.zfill(10)
    url = f"https://data.sec.gov/api/xbrl/companyfacts/CIK{cik10}.json"

    headers = {
        "User-Agent": user_agent,
        "Accept": "application/json",
        "Accept-Language": "en-US,en;q=0.9",
    }
    resp = session.get(url, headers=headers, timeout=timeout)
    resp.raise_for_status()
    payload = resp.json()

    facts = payload.get("facts") or {}
    candidates = [
        ("dei", "EntityCommonStockSharesOutstanding"),
        ("us-gaap", "CommonStockSharesOutstanding"),
    ]

    best_val: int | None = None
    best_end: date | None = None

    for taxonomy, tag in candidates:
        node = ((facts.get(taxonomy) or {}).get(tag) or {}).get("units") or {}
        for _, series in node.items():
            if not isinstance(series, list):
                continue
            for item in series:
                end = item.get("end") or item.get("fp") or item.get("filed")
                val = item.get("val")
                if val is None:
                    continue
                try:
                    end_date = parse_ymd(str(item.get("end")))
                except Exception:
                    continue
                try:
                    shares = int(val)
                except Exception:
                    continue
                if best_end is None or end_date > best_end:
                    best_end = end_date
                    best_val = shares

    if best_val is None or best_end is None:
        raise RuntimeError("No shares outstanding found in SEC companyfacts")

    return SecSharesOutstanding(shares_outstanding=best_val, as_of_date=best_end, raw_payload=payload)

