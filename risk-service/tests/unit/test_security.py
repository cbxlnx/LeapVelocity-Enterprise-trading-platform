import time

import jwt
import pytest
from fastapi import HTTPException

from app.core.config import Settings
from app.core.security import decode_token


def test_decode_token_returns_account_and_roles():
    settings = Settings(RISK_JWT_SECRET="secret", RISK_JWT_ISSUER="issuer")
    token = jwt.encode({"accountId": 7, "roles": ["ROLE_USER"], "iss": "issuer", "exp": time.time() + 60}, "secret", algorithm="HS256")

    principal = decode_token(token, settings)

    assert principal.account_id == 7
    assert principal.roles == ("ROLE_USER",)


def test_decode_token_rejects_wrong_account_claim_shape():
    settings = Settings(RISK_JWT_SECRET="secret", RISK_JWT_ISSUER="issuer")
    token = jwt.encode({"accountId": 0, "iss": "issuer", "exp": time.time() + 60}, "secret", algorithm="HS256")

    with pytest.raises(HTTPException) as error:
        decode_token(token, settings)

    assert error.value.status_code == 401
