from contextlib import AbstractAsyncContextManager
from typing import Any

import httpx

from app.core.config import Settings


class TradeApiError(RuntimeError):
    pass


class TradeApiClient(AbstractAsyncContextManager):
    def __init__(self, settings: Settings, token: str | None = None):
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        self._client = httpx.AsyncClient(base_url=settings.trade_api_base_url.rstrip("/"), headers=headers, timeout=10.0)

    async def __aenter__(self) -> "TradeApiClient":
        await self._client.__aenter__()
        return self

    async def __aexit__(self, exc_type, exc, traceback) -> None:
        await self._client.__aexit__(exc_type, exc, traceback)

    async def _get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        try:
            response = await self._client.get(path, params=params)
            response.raise_for_status()
            return response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise TradeApiError(f"Trade API read failed for {path}") from exc

    async def get_account(self, account_id: int) -> dict[str, Any]:
        return await self._get(f"/v1/accounts/{account_id}")

    async def get_positions(self, account_id: int) -> list[dict[str, Any]]:
        return await self._get(f"/v1/accounts/{account_id}/positions")

    async def get_quotes(self, symbols: list[str]) -> dict[str, Any]:
        return await self._get("/v1/market-data/quotes", {"symbols": ",".join(symbols)})

    async def get_instruments(self) -> list[dict[str, Any]]:
        return await self._get("/v1/instruments")
