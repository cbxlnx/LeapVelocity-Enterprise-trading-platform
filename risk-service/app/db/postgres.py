from contextlib import contextmanager
from typing import Iterator

import psycopg
from psycopg.rows import dict_row

from app.core.config import Settings


@contextmanager
def read_only_connection(settings: Settings) -> Iterator[psycopg.Connection]:
    connection = psycopg.connect(
        dbname=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
        host=settings.db_host,
        port=settings.db_port,
        row_factory=dict_row,
        connect_timeout=10,
    )
    try:
        connection.execute("SET TRANSACTION READ ONLY")
        yield connection
        connection.rollback()
    finally:
        connection.close()
