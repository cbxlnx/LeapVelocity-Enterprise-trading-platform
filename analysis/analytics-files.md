# Analytics page: implementation files

Data flow: Yahoo Finance → Python importer → PostgreSQL → Spring API → Angular `/analytics`.

## Data and import

- [`scripts/import_data.py`](scripts/import_data.py) — downloads daily prices with `yfinance`, reads symbols from `instruments`, and saves prices using UPSERT. Defaults to two years.
- [`requirements.txt`](requirements.txt) — Python dependencies for importing prices.
- [`../db/tables/12_daily_prices.sql`](../db/tables/12_daily_prices.sql) — historical-price table, price constraints and unique symbol/date key.

## Spring backend

Paths below are relative to `backend/src/main/java/com/leapvelocity/`:

- `controller/HistoricalPriceController.java` and `service/HistoricalPriceService.java` — serve a selected instrument's stored prices and validate date ranges.
- `repository/HistoricalPriceRepository.java` — reads prices and finds common trading dates.
- `controller/PortfolioAnalyticsController.java` and `service/PortfolioAnalyticsService.java` — account valuation, cash, P&L and allocation.
- `controller/PortfolioPerformanceController.java` and `service/PortfolioPerformanceService.java` — hypothetical portfolio performance against SPY for `1M`, `3M`, `6M`, `1Y` and `2Y`.
- `dto/response/` — response models: `HistoricalPricePointDto.java`, `HistoricalPricesDto.java`, `PortfolioHoldingDto.java`, `PortfolioSummaryDto.java` and `PortfolioPerformanceDto.java`.

## Angular frontend

Paths below are relative to `frontend/src/app/`:

- `features/analytics/pages/analytics-dashboard/analytics-dashboard.component.{ts,html,css}` — page, instrument dropdown, date filters and loading/error/empty states.
- `features/analytics/services/analytics-api.service.ts` — calls the price, summary and performance endpoints.
- `features/analytics/components/` — `price-chart`, `portfolio-summary`, `performance-panel` and `performance-chart`; each has TypeScript, HTML and CSS files.
- `features/analytics/models/` — historical-price, summary and performance types, plus trading-date helpers.
- `app.routes.ts` and `shared/layout/header/header.html` — `/analytics` route and navigation link.
- Reused `services/instrument.service.ts` — gets dropdown options from `GET /api/v1/instruments`.

## Containers

- [`Dockerfile`](Dockerfile) and [`.dockerignore`](.dockerignore) — historical-price importer image.
- [`../docker-compose.yml`](../docker-compose.yml) — starts the importer, database, backend and frontend.
- [`../frontend/Dockerfile`](../frontend/Dockerfile), [`../frontend/.dockerignore`](../frontend/.dockerignore) and [`../frontend/nginx/default.conf.template`](../frontend/nginx/default.conf.template) — build and serve Angular; proxy API/auth requests.
- [`../frontend/angular.json`](../frontend/angular.json) and [`../frontend/src/environments/environment.prod.ts`](../frontend/src/environments/environment.prod.ts) — production frontend configuration.

Historical prices come from Yahoo Finance. Account trading is simulated, seed purchase costs are illustrative, and the performance chart models the current allocation over past prices.
