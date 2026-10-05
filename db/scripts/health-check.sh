#!/bin/bash
# Health Check - Verifies database connectivity and integrity

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-${POSTGRES_PORT:-5432}}"
DB_NAME="${DB_NAME:-leapvelocity_trading_db}"
DB_USER="${DB_USER:-trading_user}"

ENV_FILE="$(dirname "$0")/../../.env"
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Database Health Check Report         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

if ! command -v psql &> /dev/null; then
    echo -e "${RED}✗ psql not found${NC}"
    exit 1
fi

echo -e "${GREEN}Testing database connectivity...${NC}"
if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Connection successful${NC}"
else
    echo -e "${RED}✗ Connection failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Table Row Counts:${NC}"

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" << 'EOF'
SELECT 'accounts' AS table_name, COUNT(*) AS rows FROM accounts
UNION ALL
SELECT 'instruments', COUNT(*) FROM instruments
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'positions', COUNT(*) FROM positions
ORDER BY table_name;
EOF

echo ""
echo -e "${GREEN}✓ Health check completed!${NC}"
