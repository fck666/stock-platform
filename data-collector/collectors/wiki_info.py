from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Iterable
from urllib.parse import unquote, urlparse

import pandas as pd
import requests


log = logging.getLogger(__name__)


@dataclass(frozen=True)
class WikiSummary:
    symbol: str
    wiki_url: str
    title: str | None
    description: str | None
    extract: str | None
    page_id: int | None


def _wiki_title_from_url(wiki_url: str) -> str | None:
    try:
        path = urlparse(wiki_url).path
        if not path.startswith("/wiki/"):
            return None
        title = path[len("/wiki/") :]
        title = unquote(title)
        return title if title else None
    except Exception:
        return None


def fetch_wikipedia_summary(
    session: requests.Session,
    wiki_url: str,
    lang: str,
    timeout: float,
    user_agent: str,
) -> dict:
    title = _wiki_title_from_url(wiki_url)
    if title is None:
        raise ValueError(f"Invalid wiki url: {wiki_url}")

    api_url = f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{requests.utils.quote(title)}"
    headers = {
        "User-Agent": user_agent,
        "Accept": "application/json",
    }
    resp = session.get(api_url, headers=headers, timeout=timeout)
    resp.raise_for_status()
    return resp.json()


def fetch_company_wiki_summaries(
    session: requests.Session,
    companies_df: pd.DataFrame,
    lang: str,
    timeout: float,
    user_agent: str,
    limit: int | None = None,
) -> pd.DataFrame:
    cols = ["symbol", "wiki_url"]
    missing = [c for c in cols if c not in companies_df.columns]
    if missing:
        raise ValueError(f"companies_df missing columns: {missing}")

    rows = companies_df[cols].dropna(subset=["wiki_url"]).copy()
    if limit is not None and limit > 0:
        rows = rows.head(limit).copy()

    records: list[dict] = []
    for _, r in rows.iterrows():
        symbol = str(r["symbol"])
        wiki_url = str(r["wiki_url"])
        try:
            payload = fetch_wikipedia_summary(
                session=session,
                wiki_url=wiki_url,
                lang=lang,
                timeout=timeout,
                user_agent=user_agent,
            )
            record = {
                "symbol": symbol,
                "wiki_url": wiki_url,
                "title": payload.get("title"),
                "description": payload.get("description"),
                "extract": payload.get("extract"),
                "page_id": payload.get("pageid"),
            }
            records.append(record)
            log.info("Fetched wiki summary for %s", symbol)
        except Exception as e:
            log.warning("Failed wiki summary for %s: %s", symbol, e)

    return pd.DataFrame.from_records(records)

