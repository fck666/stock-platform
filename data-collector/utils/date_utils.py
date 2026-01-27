from __future__ import annotations

from datetime import date, datetime, timedelta


def parse_ymd(value: str) -> date:
    return datetime.strptime(value, "%Y-%m-%d").date()


def to_ymd(value: date) -> str:
    return value.strftime("%Y-%m-%d")


def today_ymd() -> str:
    return datetime.utcnow().date().strftime("%Y-%m-%d")


def yesterday_ymd() -> str:
    return (datetime.utcnow().date() - timedelta(days=1)).strftime("%Y-%m-%d")
