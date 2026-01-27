from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv


def _get_env(name: str, default: str | None = None) -> str | None:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return value.strip()


@dataclass(frozen=True)
class Settings:
    http_timeout_seconds: float
    user_agent: str
    output_dir: str
    default_start_date: str | None
    default_end_date: str | None
    wiki_lang: str
    db_dsn: str | None


def load_settings() -> Settings:
    load_dotenv()

    return Settings(
        http_timeout_seconds=float(_get_env("HTTP_TIMEOUT_SECONDS", "20")),
        user_agent=_get_env(
            "USER_AGENT",
            "stock-platform-data-collector/0.1 (contact: example@example.com)",
        ),
        output_dir=_get_env("OUTPUT_DIR", "./output") or "./output",
        default_start_date=_get_env("DEFAULT_START_DATE", None),
        default_end_date=_get_env("DEFAULT_END_DATE", None),
        wiki_lang=_get_env("WIKI_LANG", "en") or "en",
        db_dsn=_get_env("DB_DSN", None),
    )
