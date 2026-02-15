import unittest
from datetime import date

from collectors.eodhd import fetch_eodhd_prices_range


class _FakeResponse:
    def __init__(self, status_code: int, payload, headers=None, text: str = ""):
        self.status_code = status_code
        self._payload = payload
        self.headers = headers or {}
        self.text = text

    def raise_for_status(self):
        if 400 <= self.status_code:
            raise RuntimeError(f"HTTP {self.status_code}")

    def json(self):
        return self._payload


class _FakeSession:
    def __init__(self, payload):
        self._payload = payload
        self.last_url = None

    def get(self, url, headers=None, timeout=None):
        self.last_url = url
        return _FakeResponse(200, self._payload)


class TestEodhdPrices(unittest.TestCase):
    def test_fetch_eodhd_prices_range_parses_rows(self):
        session = _FakeSession(
            [
                {"date": "2024-01-03", "open": 10, "high": 11, "low": 9, "close": 10.5, "volume": 100},
                {"date": "2024-01-02", "open": 9, "high": 10, "low": 8, "close": 9.5, "volume": 200},
            ]
        )
        df = fetch_eodhd_prices_range(
            session=session,
            eodhd_symbol="AAPL.US",
            api_token="token",
            start_date=date(2024, 1, 1),
            end_date=date(2024, 1, 5),
            freq="d",
            timeout=1,
            user_agent="ua",
        )
        self.assertEqual(list(df.columns), ["date", "open", "high", "low", "close", "volume", "symbol"])
        self.assertEqual(df["symbol"].iloc[0], "AAPL.US")
        self.assertEqual(df["date"].dt.date.tolist(), [date(2024, 1, 2), date(2024, 1, 3)])

    def test_fetch_eodhd_prices_range_empty(self):
        session = _FakeSession([])
        df = fetch_eodhd_prices_range(
            session=session,
            eodhd_symbol="AAPL.US",
            api_token="token",
            start_date=date(2024, 1, 1),
            end_date=date(2024, 1, 5),
            freq="d",
            timeout=1,
            user_agent="ua",
        )
        self.assertTrue(df.empty)


if __name__ == "__main__":
    unittest.main()

