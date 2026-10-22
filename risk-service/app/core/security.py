from dataclasses import dataclass
from typing import Any

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import Settings, get_settings


@dataclass(frozen=True)
class Principal:
    account_id: int
    roles: tuple[str, ...]
    claims: dict[str, Any]

    @property
    def is_admin(self) -> bool:
        return "ROLE_ADMIN" in self.roles or "ADMIN" in self.roles


bearer = HTTPBearer(auto_error=False)


def _roles(claims: dict[str, Any]) -> tuple[str, ...]:
    value = claims.get("roles", claims.get("role", []))
    if isinstance(value, str):
        return (value,)
    if isinstance(value, list):
        return tuple(str(item) for item in value)
    return ()


def decode_token(token: str, settings: Settings) -> Principal:
    if not settings.jwt_secret:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail={"message": "Risk JWT secret is not configured"})
    try:
        claims = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"], issuer=settings.jwt_issuer)
        account_id = int(claims["accountId"])
        if account_id <= 0:
            raise ValueError
    except (jwt.PyJWTError, KeyError, TypeError, ValueError, OverflowError) as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail={"message": "Invalid or expired access token"}) from exc
    return Principal(account_id=account_id, roles=_roles(claims), claims=claims)


def require_account_access(
    account_id: int,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    settings: Settings = Depends(get_settings),
) -> Principal:
    if credentials is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail={"message": "Bearer access token is required"})
    principal = decode_token(credentials.credentials, settings)
    if principal.account_id != account_id and not principal.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail={"message": "Account access is not permitted"})
    return principal
