from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class DbConfig:
    dsn: str


def get_connection(config: DbConfig):
    import psycopg2

    return psycopg2.connect(config.dsn)
