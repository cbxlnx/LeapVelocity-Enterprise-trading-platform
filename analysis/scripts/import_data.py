import yfinance as yf
from pathlib import Path

OUT_RAW_DATA = Path(__file__).resolve().parent.parent / "data"
START_DATE = "2020-01-01"

aapl = yf.download("AAPL", start=START_DATE)
aapl.to_csv(OUT_RAW_DATA / "AAPL.csv")

msft = yf.download("MSFT", start=START_DATE)
msft.to_csv(OUT_RAW_DATA / "MSFT.csv")

nflx = yf.download("NFLX", start=START_DATE)
nflx.to_csv(OUT_RAW_DATA / "NFLX.csv")

nvda = yf.download("NVDA", start=START_DATE)
nvda.to_csv(OUT_RAW_DATA / "NVDA.csv")

googl = yf.download("GOOGL", start=START_DATE)
googl.to_csv(OUT_RAW_DATA / "GOOGL.csv")

meta = yf.download("META", start=START_DATE)
meta.to_csv(OUT_RAW_DATA / "META.csv")

tsla = yf.download("TSLA", start=START_DATE)
tsla.to_csv(OUT_RAW_DATA / "TSLA.csv")
