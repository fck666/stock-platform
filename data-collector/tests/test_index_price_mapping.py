import unittest

from services.db_sync import _resolve_index_provider_identifier


class TestIndexPriceMapping(unittest.TestCase):
    def test_resolve_index_provider_identifier_stooq(self):
        v = _resolve_index_provider_identifier(canonical_symbol="^SPX", provider="stooq")
        self.assertEqual(v, "^spx")

    def test_resolve_index_provider_identifier_eodhd(self):
        v = _resolve_index_provider_identifier(canonical_symbol="^SPX", provider="eodhd")
        self.assertEqual(v, "GSPC.INDX")

    def test_resolve_etf_provider_identifier_eodhd(self):
        v = _resolve_index_provider_identifier(canonical_symbol="IWM", provider="eodhd")
        self.assertEqual(v, "IWM.US")


if __name__ == "__main__":
    unittest.main()

