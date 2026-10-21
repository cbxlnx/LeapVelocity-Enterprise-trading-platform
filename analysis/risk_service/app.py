"""Internal HTTP adapter. Importer and yfinance are never imported here."""

import hmac
import os
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import APIKeyHeader

from .calculator import calculate
from .schemas import CalculationRequest, CalculationResponse


@asynccontextmanager
async def lifespan(application):
    token = os.environ.get("RISK_SERVICE_TOKEN", "")
    if not token or any(ord(character) < 33 or ord(character) > 126 for character in token):
        raise RuntimeError("RISK_SERVICE_TOKEN is required and must contain printable ASCII without spaces")
    application.state.token = token
    yield


app = FastAPI(title="LeapVelocity internal risk calculator", version="1.0", lifespan=lifespan,
              docs_url=None, redoc_url=None)
token_header = APIKeyHeader(name="X-Risk-Service-Token", auto_error=False)


def authenticate(token: Annotated[str | None, Depends(token_header)]):
    if token is None or not hmac.compare_digest(token.encode(), app.state.token.encode()):
        raise HTTPException(401, "Invalid service credentials")


@app.exception_handler(RequestValidationError)
async def validation_error(_request, _exception):
    # Never echo the snapshot or credentials in a validation response.
    return JSONResponse(status_code=422, content={"detail": "Invalid v1 calculation snapshot"})


@app.get("/health")
def health():
    return {"status": "ok", "schemaVersion": "1.0"}


@app.post("/internal/v1/risk/calculate", response_model=CalculationResponse,
          dependencies=[Depends(authenticate)])
def calculate_snapshot(snapshot: CalculationRequest):
    return calculate(snapshot)
