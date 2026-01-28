from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from typing import Iterable
from urllib.parse import urljoin

import pandas as pd
import requests
from bs4 import BeautifulSoup

log = logging.getLogger(__name__)

WIKI_HSI_URL = "https://zh-yue.wikipedia.org/wiki/%E6%81%92%E7%94%9F%E6%8C%87%E6%95%B8"
WIKI_HSTECH_URL = "https://zh-yue.wikipedia.org/wiki/%E6%81%92%E7%94%9F%E7%A7%91%E6%8A%80%E6%8C%87%E6%95%B8"


def normalize_hk_symbol_for_stooq(symbol: str) -> str:
    # Stooq HK format: stock number without leading zeros + .HK (uppercase)
    # e.g. 00001 -> 1.HK, 00700 -> 700.HK
    s = symbol.strip().lstrip("0")
    if not s: # handle case where it might be all zeros
        s = "0"
    return f"{s}.HK"


def _extract_from_soup(soup: BeautifulSoup, records: list[dict]):
    # Try to find all tables and lists that might contain HKEX symbols
    for tag in soup.find_all(["table", "ul"]):
        if "港交所" in tag.get_text():
            items = tag.find_all("tr") if tag.name == "table" else tag.find_all("li")
            for item in items:
                text = item.get_text()
                # Pattern: CompanyName (HKEX: Symbol)
                # e.g. "長和（港交所：00001）"
                match = re.search(r"([^（(]+)[（(]港交所：(\d+)[）)]", text)
                if not match:
                    continue
                
                name_text = match.group(1).strip()
                code = match.group(2).strip()
                
                # Find the specific link that wraps the company name or is near it
                # We prefer a valid /wiki/ link
                wiki_url = None
                links = item.find_all("a")
                for a in links:
                    href = a.get("href")
                    if not href or href.startswith("#") or "action=edit" in href:
                        continue
                    if "/wiki/" in href:
                        # Prefer the one whose text or title matches the name
                        if name_text in a.get_text() or name_text in (a.get("title") or ""):
                            wiki_url = urljoin("https://zh-yue.wikipedia.org/", href)
                            break
                        # Fallback to the first valid wiki link
                        if not wiki_url:
                            wiki_url = urljoin("https://zh-yue.wikipedia.org/", href)
                
                # If still no wiki_url but we have a name, try to construct a zh.wikipedia link as fallback
                # because many HK companies have zh pages but not zh-yue pages
                if not wiki_url:
                    # Use the title from the first link if available, even if it's a redlink
                    first_a = item.find("a")
                    if first_a:
                        title = first_a.get("title") or name_text
                        wiki_url = f"https://zh.wikipedia.org/wiki/{requests.utils.quote(title)}"

                records.append({
                    "symbol": code,
                    "stooq_symbol": normalize_hk_symbol_for_stooq(code),
                    "security": name_text,
                    "wiki_url": wiki_url,
                })


def fetch_hsi_components(session: requests.Session, timeout: float, user_agent: str) -> pd.DataFrame:
    headers = {"User-Agent": user_agent}
    resp = session.get(WIKI_HSI_URL, headers=headers, timeout=timeout)
    resp.raise_for_status()

    soup = BeautifulSoup(resp.text, "html.parser")
    records = []
    _extract_from_soup(soup, records)

    df = pd.DataFrame.from_records(records).drop_duplicates(subset=["symbol"])
    log.info("Loaded %s HSI components", len(df))
    return df


def fetch_hstech_components(session: requests.Session, timeout: float, user_agent: str) -> pd.DataFrame:
    headers = {"User-Agent": user_agent}
    resp = session.get(WIKI_HSTECH_URL, headers=headers, timeout=timeout)
    resp.raise_for_status()

    soup = BeautifulSoup(resp.text, "html.parser")
    records = []
    _extract_from_soup(soup, records)

    df = pd.DataFrame.from_records(records).drop_duplicates(subset=["symbol"])
    log.info("Loaded %s HSTECH components", len(df))
    return df
