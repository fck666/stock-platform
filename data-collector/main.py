from __future__ import annotations

import argparse
import os

from config.settings import load_settings
from services.full_sync import run_full_sync
from services.db_sync import (
    sync_indices_prices_to_db,
    sync_index_daily_incremental,
    sync_index_fundamentals_to_db,
    sync_index_prices_to_db,
    sync_index_wiki_to_db,
    sync_sp500_daily_incremental,
    sync_sp500_prices_to_db,
    sync_sp500_wiki_to_db,
)
from utils.date_utils import yesterday_ymd
from utils.logger import setup_logging


def _parse_csv_list(value: str | None) -> list[str] | None:
    if value is None:
        return None
    items = [x.strip() for x in value.split(",")]
    items = [x for x in items if x]
    return items or None


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="data-collector", add_help=True)
    p.add_argument("--log-level", default="INFO")

    sub = p.add_subparsers(dest="command", required=True)

    full = sub.add_parser("full-sync")
    full.add_argument("--start", dest="start_date", default=None, help="YYYY-MM-DD")
    full.add_argument("--end", dest="end_date", default=None, help="YYYY-MM-DD")
    full.add_argument("--limit", type=int, default=20)
    full.add_argument("--symbols", default=None, help="Comma-separated, e.g. AAPL,MSFT")
    full.add_argument("--output", choices=["console", "excel"], default="excel")
    full.add_argument("--out-dir", default=None)
    full.add_argument("--preview-rows", type=int, default=20)
    full.add_argument("--no-stock-prices-in-excel", action="store_true", default=False)
    full.add_argument("--stock-prices-max-rows", type=int, default=200_000)
    full.add_argument("--no-export-prices-csv", action="store_true", default=False)

    wiki = sub.add_parser("db-sync-wiki")
    wiki.add_argument("--db-dsn", default=None)
    wiki.add_argument("--index", default="^SPX", help="Index symbol, e.g. ^SPX, ^HSI, ^HSTECH")
    wiki.add_argument("--limit", type=int, default=None)
    wiki.add_argument("--symbols", default=None, help="Comma-separated, e.g. AAPL,MSFT")

    fund = sub.add_parser("db-sync-fundamentals")
    fund.add_argument("--db-dsn", default=None)
    fund.add_argument("--index", default="^SPX", help="Index symbol, e.g. ^SPX, ^HSI, ^HSTECH")
    fund.add_argument("--limit", type=int, default=None)
    fund.add_argument("--symbols", default=None, help="Comma-separated, e.g. AAPL,MSFT")

    prices = sub.add_parser("db-full-prices")
    prices.add_argument("--db-dsn", default=None)
    prices.add_argument("--index", default="^SPX", help="Index symbol, e.g. ^SPX, ^HSI, ^HSTECH")
    prices.add_argument("--start", dest="start_date", default="2016-01-01", help="YYYY-MM-DD")
    prices.add_argument("--end", dest="end_date", default="2025-12-31", help="YYYY-MM-DD")
    prices.add_argument("--interval", default="1d", choices=["1d", "1w", "1m", "1q", "1y"])
    prices.add_argument("--limit", type=int, default=None)
    prices.add_argument("--symbols", default=None, help="Comma-separated, e.g. AAPL,MSFT")
    prices.add_argument("--no-indices", action="store_true", default=False)

    daily = sub.add_parser("db-daily-sync")
    daily.add_argument("--db-dsn", default=None)
    daily.add_argument("--index", default="^SPX", help="Index symbol, e.g. ^SPX, ^HSI, ^HSTECH")
    daily.add_argument("--interval", default="1d", choices=["1d", "1w", "1m", "1q", "1y"])
    daily.add_argument("--limit", type=int, default=None)
    daily.add_argument("--symbols", default=None, help="Comma-separated, e.g. AAPL,MSFT")
    daily.add_argument("--no-indices", action="store_true", default=False)

    indices = sub.add_parser("db-sync-indices")
    indices.add_argument("--db-dsn", default=None)
    indices.add_argument("--start", dest="start_date", default="2016-01-01", help="YYYY-MM-DD")
    indices.add_argument("--end", dest="end_date", default=None, help="YYYY-MM-DD")
    indices.add_argument("--interval", default="1d", choices=["1d", "1w", "1m", "1q", "1y"])

    return p


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    setup_logging(args.log_level)
    settings = load_settings()

    if args.command == "full-sync":
        out_dir = args.out_dir or settings.output_dir
        out_dir = os.path.abspath(out_dir)

        run_full_sync(
            start_date=args.start_date or settings.default_start_date,
            end_date=args.end_date or settings.default_end_date,
            symbols=_parse_csv_list(args.symbols),
            limit=args.limit,
            indices=None,
            output=args.output,
            out_dir=out_dir,
            wiki_lang=settings.wiki_lang,
            http_timeout_seconds=settings.http_timeout_seconds,
            user_agent=settings.user_agent,
            preview_rows=args.preview_rows,
            include_stock_prices_in_excel=not args.no_stock_prices_in_excel,
            stock_prices_max_rows=args.stock_prices_max_rows,
            export_prices_csv=not args.no_export_prices_csv,
        )
        return 0

    if args.command == "db-sync-wiki":
        db_dsn = args.db_dsn or settings.db_dsn
        if not db_dsn:
            raise RuntimeError("DB_DSN is not configured")
        sync_index_wiki_to_db(
            db_dsn=db_dsn,
            index_symbol=args.index,
            symbols=_parse_csv_list(args.symbols),
            limit=args.limit,
            wiki_lang=settings.wiki_lang,
            http_timeout_seconds=settings.http_timeout_seconds,
            user_agent=settings.user_agent,
        )
        return 0

    if args.command == "db-sync-fundamentals":
        db_dsn = args.db_dsn or settings.db_dsn
        if not db_dsn:
            raise RuntimeError("DB_DSN is not configured")
        sync_index_fundamentals_to_db(
            db_dsn=db_dsn,
            index_symbol=args.index,
            symbols=_parse_csv_list(args.symbols),
            limit=args.limit,
            http_timeout_seconds=settings.http_timeout_seconds,
            user_agent=settings.user_agent,
        )
        return 0

    if args.command == "db-full-prices":
        db_dsn = args.db_dsn or settings.db_dsn
        if not db_dsn:
            raise RuntimeError("DB_DSN is not configured")
        sync_index_prices_to_db(
            db_dsn=db_dsn,
            index_symbol=args.index,
            start_date=args.start_date,
            end_date=args.end_date,
            interval=args.interval,
            symbols=_parse_csv_list(args.symbols),
            limit=args.limit,
            http_timeout_seconds=settings.http_timeout_seconds,
            user_agent=settings.user_agent,
            include_indices=not args.no_indices,
        )
        return 0

    if args.command == "db-daily-sync":
        db_dsn = args.db_dsn or settings.db_dsn
        if not db_dsn:
            raise RuntimeError("DB_DSN is not configured")
        sync_index_daily_incremental(
            db_dsn=db_dsn,
            index_symbol=args.index,
            interval=args.interval,
            symbols=_parse_csv_list(args.symbols),
            limit=args.limit,
            http_timeout_seconds=settings.http_timeout_seconds,
            user_agent=settings.user_agent,
            include_indices=not args.no_indices,
        )
        return 0

    if args.command == "db-sync-indices":
        db_dsn = args.db_dsn or settings.db_dsn
        if not db_dsn:
            raise RuntimeError("DB_DSN is not configured")
        sync_indices_prices_to_db(
            db_dsn=db_dsn,
            start_date=args.start_date,
            end_date=args.end_date or yesterday_ymd(),
            interval=args.interval,
            http_timeout_seconds=settings.http_timeout_seconds,
            user_agent=settings.user_agent,
        )
        return 0

    raise RuntimeError(f"Unknown command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
