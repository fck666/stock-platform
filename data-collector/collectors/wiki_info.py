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


def _wiki_info_from_url(wiki_url: str) -> tuple[str, str] | None:
    try:
        parsed = urlparse(wiki_url)
        host = parsed.netloc
        path = parsed.path
        
        # Extract language from host, e.g. zh-yue.wikipedia.org -> zh-yue
        lang = "en"
        if host.endswith(".wikipedia.org"):
            lang = host[: -len(".wikipedia.org")]
        
        if not path.startswith("/wiki/"):
            return None
        title = path[len("/wiki/") :]
        title = unquote(title)
        return lang, title if title else None
    except Exception:
        return None


def fetch_wikipedia_summary(
    session: requests.Session,
    wiki_url: str,
    lang_override: str | None,
    timeout: float,
    user_agent: str,
) -> dict:
    info = _wiki_info_from_url(wiki_url)
    if info is None:
        raise ValueError(f"Invalid wiki url or not a standard /wiki/ path: {wiki_url}")
    
    url_lang, title = info
    lang = lang_override or url_lang

    api_url = f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{requests.utils.quote(title)}"
    headers = {
        "User-Agent": user_agent,
        "Accept": "application/json",
    }
    resp = session.get(api_url, headers=headers, timeout=timeout)
    
    # If 404 and we were using an override or a specific lang, try falling back to the URL's own lang or 'zh'
    if resp.status_code == 404 and lang != url_lang:
        log.info("Wiki summary 404 for %s in %s, falling back to %s", title, lang, url_lang)
        api_url = f"https://{url_lang}.wikipedia.org/api/rest_v1/page/summary/{requests.utils.quote(title)}"
        resp = session.get(api_url, headers=headers, timeout=timeout)
    
    # Final fallback for HK stocks: if still 404 and it's zh-yue, try zh
    if resp.status_code == 404 and url_lang == "zh-yue" and lang != "zh":
        log.info("Wiki summary 404 for %s, trying zh.wikipedia.org", title)
        api_url = f"https://zh.wikipedia.org/api/rest_v1/page/summary/{requests.utils.quote(title)}"
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
                lang_override=lang,
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

