from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(case_sensitive=False, extra="ignore")

    jwt_secret: str = Field(default="", validation_alias="RISK_JWT_SECRET")
    jwt_issuer: str = Field(default="leapvelocity-auth", validation_alias="RISK_JWT_ISSUER")
    trade_api_base_url: str = Field(default="http://trade-api:8081/api", validation_alias="RISK_TRADE_API_BASE_URL")
    db_name: str = Field(default="leapvelocity_trading_db", validation_alias="RISK_DB_NAME")
    db_user: str = Field(default="postgres", validation_alias="RISK_DB_USER")
    db_password: str = Field(default="", validation_alias="RISK_DB_PASSWORD")
    db_host: str = Field(default="trade-db", validation_alias="RISK_DB_HOST")
    db_port: int = Field(default=5432, validation_alias="RISK_DB_PORT")
    benchmark: str = Field(default="SPY", validation_alias="RISK_BENCHMARK")
    default_lookback_days: int = Field(default=252, validation_alias="RISK_DEFAULT_LOOKBACK_DAYS")
    default_confidence: float = Field(default=0.95, validation_alias="RISK_DEFAULT_CONFIDENCE")
    default_mc_paths: int = Field(default=10000, validation_alias="RISK_DEFAULT_MC_PATHS")
    minimum_observations: int = Field(default=30, validation_alias="RISK_MINIMUM_OBSERVATIONS")


@lru_cache
def get_settings() -> Settings:
    return Settings()
