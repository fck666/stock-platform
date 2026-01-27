from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class DailySyncResult:
    updated: int


def run_daily_sync(**_) -> DailySyncResult:
    raise NotImplementedError("Daily sync will be implemented after database schema is finalized.")
