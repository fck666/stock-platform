from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Iterable
from urllib.parse import urljoin

import pandas as pd
import requests
from bs4 import BeautifulSoup


log = logging.getLogger(__name__)


WIKI_SP500_URL = "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies"


@dataclass(frozen=True)
class Sp500Company:
    symbol: str
    security: str | None
    gics_sector: str | None
    gics_sub_industry: str | None
    headquarters: str | None
    date_first_added: str | None
    cik: str | None
    founded: str | None
    wiki_url: str | None


def normalize_us_symbol_for_stooq(symbol: str) -> str:
    return symbol.strip().lower().replace(".", "-") + ".us"


def fetch_sp500_companies(session: requests.Session, timeout: float, user_agent: str) -> pd.DataFrame:
    headers = {"User-Agent": user_agent}
    resp = session.get(WIKI_SP500_URL, headers=headers, timeout=timeout)
    resp.raise_for_status()

    soup = BeautifulSoup(resp.text, "html.parser")
    table = soup.find("table", {"id": "constituents"})
    if table is None:
        raise RuntimeError("Failed to locate constituents table on Wikipedia page.")

    rows = table.find_all("tr")
    if not rows:
        raise RuntimeError("S&P 500 table appears empty.")

    records: list[dict] = []
    for tr in rows[1:]:
        tds = tr.find_all(["td", "th"])
        if len(tds) < 8:
            continue

        symbol = tds[0].get_text(strip=True)
        security_cell = tds[1]
        security = security_cell.get_text(strip=True) or None
        a = security_cell.find("a")
        wiki_url = urljoin("https://en.wikipedia.org/", a["href"]) if a and a.get("href") else None

        record = {
            "symbol": symbol,
            "stooq_symbol": normalize_us_symbol_for_stooq(symbol),
            "security": security,
            "gics_sector": tds[2].get_text(strip=True) or None,
            "gics_sub_industry": tds[3].get_text(strip=True) or None,
            "headquarters": tds[4].get_text(strip=True) or None,
            "date_first_added": tds[5].get_text(strip=True) or None,
            "cik": tds[6].get_text(strip=True) or None,
            "founded": tds[7].get_text(strip=True) or None,
            "wiki_url": wiki_url,
        }
        records.append(record)

    df = pd.DataFrame.from_records(records)
    log.info("Loaded %s S&P 500 tickers", len(df))
    return df


def filter_symbols(df: pd.DataFrame, symbols: Iterable[str] | None, limit: int | None) -> pd.DataFrame:
    out = df
    if symbols:
        symbol_set = {s.strip().upper() for s in symbols if s.strip()}
        out = out[out["symbol"].isin(symbol_set)].copy()

    if limit is not None and limit > 0:
        out = out.head(limit).copy()

    return out.reset_index(drop=True)

