#!/bin/bash
# Initialize Database - Creates schema and loads seed data

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-leapvelocity_trading_db}"
DB_USER="${DB_USER:-trading_user}"

if [ -f "$(dirname "$0")/../.env" ]; then
    source "$(dirname "$0")/../.env"
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Initializing Database: $DB_NAME${NC}"

if ! command -v psql &> /dev/null; then
    echo -e "${RED}Error: psql not found. Install PostgreSQL client tools.${NC}"
    exit 1
fi

if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; then
    echo -e "${RED}Error: Cannot connect to database.${NC}"
    exit 1
fi

SCHEMA_DIR="$(dirname "$0")/../sql/schemas"
SEEDS_DIR="$(dirname "$0")/../sql/seeds"

echo -e "${YELLOW}Creating schema...${NC}"
for schema_file in "$SCHEMA_DIR"/[0-9]*.sql; do
    [ -f "$schema_file" ] && psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$schema_file" > /dev/null 2>&1 && echo -e "  ${GREEN}✓ $(basename $schema_file)${NC}"
done

echo -e "${YELLOW}Loading seed data...${NC}"
for seed_file in "$SEEDS_DIR"/[0-9]*.sql; do
    [ -f "$seed_file" ] && psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$seed_file" > /dev/null 2>&1 && echo -e "  ${GREEN}✓ $(basename $seed_file)${NC}"
done

echo -e "${GREEN}✓ Database initialized successfully!${NC}"