from datetime import date
from typing import Any

import psycopg


class HistoricalPricesRepository:
    def __init__(self, connection: psycopg.Connection):
        self.connection = connection

    def get_adjusted_close(self, symbols: list[str], start: date, end: date) -> list[dict[str, Any]]:
        if not symbols:
            return []
        query = """
            SELECT symbol, price_date, adjusted_close
            FROM analytics.daily_prices
            WHERE symbol = ANY(%s)
              AND price_date >= %s
              AND price_date < %s
            ORDER BY price_date, symbol
        """
        return list(self.connection.execute(query, (symbols, start, end)).fetchall())
