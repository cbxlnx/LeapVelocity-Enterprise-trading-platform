from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Path, Query, status
from fastapi.security import HTTPAuthorizationCredentials

from app.clients.trade_api_client import TradeApiError
from app.core.config import Settings, get_settings
from app.core.security import Principal, bearer, require_account_access
from app.schemas.risk_models import CustomShockRequest, CustomShockResponse, RiskDashboardData
from app.services.risk_engine import RiskEngine

router = APIRouter(prefix="/risk-api/v1", tags=["risk"])


def get_engine(settings: Settings = Depends(get_settings)) -> RiskEngine:
    return RiskEngine(settings)


@router.get("/accounts/{account_id}/risk/dashboard", response_model=RiskDashboardData)
async def get_dashboard(
    account_id: int = Path(gt=0),
    horizon: Literal[1, 10, 30] = Query(default=1),
    lookback_days: int = Query(default=252, alias="lookbackDays", ge=30, le=2000),
    confidence: float = Query(default=0.95, ge=0.8, lt=1.0),
    benchmark: str = Query(default="SPY", min_length=1, max_length=20),
    mc_paths: int = Query(default=10000, alias="mcPaths", ge=100, le=100000),
    principal: Principal = Depends(require_account_access),
    settings: Settings = Depends(get_settings),
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    engine: RiskEngine = Depends(get_engine),
) -> RiskDashboardData:
    del horizon, principal, settings
    if credentials is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail={"message": "Bearer access token is required"})
    try:
        payload = await engine.build_dashboard(account_id, credentials.credentials, lookback_days, confidence, benchmark.upper(), mc_paths)
        return RiskDashboardData.model_validate(payload)
    except TradeApiError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail={"message": str(exc)}) from exc


@router.post("/accounts/{account_id}/risk/custom-shock")
async def run_custom_shock(
    request: CustomShockRequest,
    account_id: int = Path(gt=0),
    principal: Principal = Depends(require_account_access),
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    engine: RiskEngine = Depends(get_engine),
) -> CustomShockResponse:
    del principal
    if credentials is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail={"message": "Bearer access token is required"})
    try:
        payload = await engine.build_custom_shock(account_id, credentials.credentials, request)
        return CustomShockResponse.model_validate(payload)
    except TradeApiError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail={"message": str(exc)}) from exc
