#!/bin/bash
# Reset Database - DROPS ALL TABLES AND RECREATES

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-${POSTGRES_PORT:-5432}}"
DB_NAME="${DB_NAME:-leapvelocity_trading_db}"
DB_USER="${DB_USER:-trading_user}"

ENV_FILE="$(dirname "$0")/../../.env"
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

echo -e "${RED}╔════════════════════════════════════════╗${NC}"
echo -e "${RED}║  WARNING: DESTRUCTIVE OPERATION!      ║${NC}"
echo -e "${RED}║  This will DELETE all data in:        ║${NC}"
echo -e "${RED}║  $DB_NAME${NC}"
echo -e "${RED}╚════════════════════════════════════════╝${NC}"
echo ""

read -p "Type the database name to confirm: " -r
echo

if [[ ! $REPLY == "$DB_NAME" ]]; then
    echo -e "${YELLOW}Reset cancelled.${NC}"
    exit 0
fi

echo -e "${YELLOW}Resetting database...${NC}"

if ! command -v psql &> /dev/null; then
    echo -e "${RED}Error: psql not found${NC}"
    exit 1
fi

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" << EOF
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS positions CASCADE;
DROP TABLE IF EXISTS instruments CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;
EOF

echo -e "${YELLOW}Recreating schema and seed data...${NC}"
"$(dirname "$0")/init-db.sh"

echo -e "${GREEN}✓ Database reset completed!${NC}"
