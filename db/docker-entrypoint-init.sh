#!/bin/sh
set -e

run_sql_dir() {
    dir="$1"

    for sql_file in "$dir"/*.sql; do
        [ -f "$sql_file" ] || continue
        echo "Running $sql_file"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$sql_file"
    done
}

run_sql_dir /docker-entrypoint-initdb.d/tables
run_sql_dir /docker-entrypoint-initdb.d/data
