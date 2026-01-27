from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Iterable

import pandas as pd
import requests

from collectors.sp500_list import fetch_sp500_companies, filter_symbols
from collectors.stooq_price import PriceQuery, fetch_many_daily_prices
from collectors.wiki_info import fetch_company_wiki_summaries
from utils.date_utils import parse_ymd


log = logging.getLogger(__name__)


DEFAULT_INDICES: dict[str, str] = {
    "S&P 500": "^spx",
    "Dow Jones Industrial": "^dji",
    "Nasdaq Composite": "^ndq",
    "Russell 2000 (ETF proxy)": "iwm.us",
    "VIX (ETF proxy)": "vixy.us",
}


@dataclass(frozen=True)
class FullSyncResult:
    companies: pd.DataFrame
    wiki_summaries: pd.DataFrame
    index_prices: pd.DataFrame
    stock_prices: dict[str, pd.DataFrame]
    output_path: str | None


def _ensure_dir(path: str) -> str:
    Path(path).mkdir(parents=True, exist_ok=True)
    return path


def _timestamp() -> str:
    return datetime.utcnow().strftime("%Y%m%d_%H%M%S")


def _export_console(
    companies: pd.DataFrame,
    wiki_summaries: pd.DataFrame,
    index_prices: pd.DataFrame,
    stock_prices: dict[str, pd.DataFrame],
    preview_rows: int,
) -> None:
    pd.set_option("display.max_columns", 50)
    pd.set_option("display.width", 200)

    print("\n[S&P 500 Companies]")
    print(companies.head(preview_rows).to_string(index=False))

    if not wiki_summaries.empty:
        print("\n[Wikipedia Summaries]")
        print(wiki_summaries.head(preview_rows).to_string(index=False))

    if not index_prices.empty:
        print("\n[Indices Prices]")
        print(index_prices.head(preview_rows).to_string(index=False))

    if stock_prices:
        first_symbol = next(iter(stock_prices.keys()))
        print(f"\n[Sample Stock Prices: {first_symbol}]")
        print(stock_prices[first_symbol].head(preview_rows).to_string(index=False))


def _export_excel(
    out_dir: str,
    companies: pd.DataFrame,
    wiki_summaries: pd.DataFrame,
    index_prices: pd.DataFrame,
    stock_prices: dict[str, pd.DataFrame],
    include_stock_prices: bool,
    stock_prices_max_rows: int,
) -> str:
    out_dir = _ensure_dir(out_dir)
    file_path = os.path.join(out_dir, f"full_sync_{_timestamp()}.xlsx")

    with pd.ExcelWriter(file_path, engine="openpyxl") as writer:
        companies.to_excel(writer, sheet_name="sp500_companies", index=False)
        if not wiki_summaries.empty:
            wiki_summaries.to_excel(writer, sheet_name="wiki_summaries", index=False)
        if not index_prices.empty:
            index_prices.to_excel(writer, sheet_name="indices_prices", index=False)

        if include_stock_prices and stock_prices:
            combined = pd.concat(stock_prices.values(), ignore_index=True)
            if len(combined) > stock_prices_max_rows:
                combined = combined.tail(stock_prices_max_rows).reset_index(drop=True)
            combined.to_excel(writer, sheet_name="stock_prices", index=False)

    return file_path


def _export_prices_csv(out_dir: str, stock_prices: dict[str, pd.DataFrame]) -> str:
    base = Path(_ensure_dir(out_dir)) / "prices"
    base.mkdir(parents=True, exist_ok=True)
    for symbol, df in stock_prices.items():
        safe = symbol.replace("^", "idx_").replace("/", "_")
        df.to_csv(base / f"{safe}.csv", index=False)
    return str(base)


def run_full_sync(
    *,
    start_date: str | None,
    end_date: str | None,
    symbols: Iterable[str] | None,
    limit: int | None,
    indices: dict[str, str] | None,
    output: str,
    out_dir: str,
    wiki_lang: str,
    http_timeout_seconds: float,
    user_agent: str,
    preview_rows: int = 20,
    include_stock_prices_in_excel: bool = True,
    stock_prices_max_rows: int = 200_000,
    export_prices_csv: bool = True,
) -> FullSyncResult:
    start: date | None = parse_ymd(start_date) if start_date else None
    end: date | None = parse_ymd(end_date) if end_date else None
    indices = indices or DEFAULT_INDICES

    with requests.Session() as session:
        companies = fetch_sp500_companies(session, timeout=http_timeout_seconds, user_agent=user_agent)
        companies = filter_symbols(companies, symbols=symbols, limit=limit)

        wiki_summaries = fetch_company_wiki_summaries(
            session=session,
            companies_df=companies,
            lang=wiki_lang,
            timeout=http_timeout_seconds,
            user_agent=user_agent,
            limit=limit,
        )

        stock_queries = [
            PriceQuery(stooq_symbol=s, start_date=start, end_date=end) for s in companies["stooq_symbol"].tolist()
        ]
        stock_prices = fetch_many_daily_prices(
            session=session,
            queries=stock_queries,
            timeout=http_timeout_seconds,
            user_agent=user_agent,
        )

        index_queries = [PriceQuery(stooq_symbol=s, start_date=start, end_date=end) for s in indices.values()]
        index_prices_map = fetch_many_daily_prices(
            session=session,
            queries=index_queries,
            timeout=http_timeout_seconds,
            user_agent=user_agent,
        )

    index_prices = pd.concat(index_prices_map.values(), ignore_index=True) if index_prices_map else pd.DataFrame()
    if not index_prices.empty:
        name_by_symbol = {v: k for k, v in indices.items()}
        index_prices["index_name"] = index_prices["symbol"].map(name_by_symbol).fillna(index_prices["symbol"])

    output_path: str | None = None
    if output == "console":
        _export_console(
            companies=companies,
            wiki_summaries=wiki_summaries,
            index_prices=index_prices,
            stock_prices=stock_prices,
            preview_rows=preview_rows,
        )
    elif output == "excel":
        output_path = _export_excel(
            out_dir=out_dir,
            companies=companies,
            wiki_summaries=wiki_summaries,
            index_prices=index_prices,
            stock_prices=stock_prices,
            include_stock_prices=include_stock_prices_in_excel,
            stock_prices_max_rows=stock_prices_max_rows,
        )
        if export_prices_csv:
            _export_prices_csv(out_dir=out_dir, stock_prices=stock_prices)
        log.info("Exported to %s", output_path)
    else:
        raise ValueError("output must be one of: console, excel")

    return FullSyncResult(
        companies=companies,
        wiki_summaries=wiki_summaries,
        index_prices=index_prices,
        stock_prices=stock_prices,
        output_path=output_path,
    )
