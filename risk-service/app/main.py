from fastapi import FastAPI

from app.api.risk_routes import router

app = FastAPI(title="LeapVelocity Risk API", version="1.0.0")
app.include_router(router)


@app.get("/healthz", tags=["health"])
def health() -> dict[str, str]:
    return {"status": "ok"}
