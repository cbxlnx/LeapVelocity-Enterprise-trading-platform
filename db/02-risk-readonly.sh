#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=risk_user="$RISK_DB_USER" \
    --set=risk_password="$RISK_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'risk_user', :'risk_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'risk_user')\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'risk_user')\gexec
SELECT format('GRANT USAGE ON SCHEMA analytics TO %I', :'risk_user')\gexec
SELECT format('GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO %I', :'risk_user')\gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA analytics GRANT SELECT ON TABLES TO %I', current_user, :'risk_user')\gexec
SQL