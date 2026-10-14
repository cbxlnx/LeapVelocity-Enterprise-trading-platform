FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY publisher.py .

RUN useradd --create-home --uid 10001 publisher
USER publisher

CMD ["python", "publisher.py"]
