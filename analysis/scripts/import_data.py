import yfinance as yf
from pathlib import Path

OUT_RAW_DATA = Path(__file__).resolve().parent.parent / "data"
START_DATE = "2020-01-01"

print("Starting data input...")
print("Downloading Equitites...")

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

dis = yf.download("DIS", start=START_DATE)
dis.to_csv(OUT_RAW_DATA / "DIS.csv")

nke = yf.download("NKE", start=START_DATE)
nke.to_csv(OUT_RAW_DATA / "NKE.csv")

ba = yf.download("BA", start=START_DATE)
ba.to_csv(OUT_RAW_DATA / "BA.csv")

print("Equities downloaded")
print("Downloading ETFs...")

spy = yf.download("SPY", start=START_DATE)
spy.to_csv(OUT_RAW_DATA / "SPY.csv")

qqq = yf.download("QQQ", start=START_DATE)
qqq.to_csv(OUT_RAW_DATA / "QQQ.csv")

dia = yf.download("DIA", start=START_DATE)
dia.to_csv(OUT_RAW_DATA / "DIA.csv")

iwm = yf.download("IWM", start=START_DATE)
iwm.to_csv(OUT_RAW_DATA / "IWM.csv")

vti = yf.download("VTI", start=START_DATE)
vti.to_csv(OUT_RAW_DATA / "VTI.csv")

efa = yf.download("EFA", start=START_DATE)
efa.to_csv(OUT_RAW_DATA / "EFA.csv")

xlk = yf.download("XLK", start=START_DATE)
xlk.to_csv(OUT_RAW_DATA / "XLK.csv")

vnq = yf.download("VNQ", start=START_DATE)
vnq.to_csv(OUT_RAW_DATA / "VNQ.csv")

gdx = yf.download("GDX", start=START_DATE)
gdx.to_csv(OUT_RAW_DATA / "GDX.csv")

slv = yf.download("SLV", start=START_DATE)
slv.to_csv(OUT_RAW_DATA / "SLV.csv")

print("ETFs downloaded")
print("Downloading Cryptocurrencies...")

btc = yf.download("BTC-USD", start=START_DATE)
btc.to_csv(OUT_RAW_DATA / "BTC-USD.csv")

eth = yf.download("ETH-USD", start=START_DATE)
eth.to_csv(OUT_RAW_DATA / "ETH-USD.csv")

sol = yf.download("SOL-USD", start=START_DATE)
sol.to_csv(OUT_RAW_DATA / "SOL-USD.csv")

doge = yf.download("DOGE-USD", start=START_DATE)
doge.to_csv(OUT_RAW_DATA / "DOGE-USD.csv")

bnb = yf.download("BNB-USD", start=START_DATE)
bnb.to_csv(OUT_RAW_DATA / "BNB-USD.csv")

print("Cryptocurrencies downloaded")
print("Downloading Bonds...")

tlt = yf.download("TLT", start=START_DATE)
tlt.to_csv(OUT_RAW_DATA / "TLT.csv")

tlh = yf.download("TLH", start=START_DATE)
tlh.to_csv(OUT_RAW_DATA / "TLH.csv")

ief = yf.download("IEF", start=START_DATE)
ief.to_csv(OUT_RAW_DATA / "IEF.csv")

iei = yf.download("IEI", start=START_DATE)
iei.to_csv(OUT_RAW_DATA / "IEI.csv")

shy = yf.download("SHY", start=START_DATE)
shy.to_csv(OUT_RAW_DATA / "SHY.csv")

print("Bonds downloaded")
print("Data import completed")
