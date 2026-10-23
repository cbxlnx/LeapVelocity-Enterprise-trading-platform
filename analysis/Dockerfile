FROM python:3.12-slim

WORKDIR /app/analysis
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HOME=/tmp

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY scripts/import_data.py scripts/import_data.py

USER 10001:10001
ENTRYPOINT ["python", "scripts/import_data.py"]
