#!/bin/bash
# Restore Database from Backup

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

if [ -z "$1" ]; then
    echo -e "${RED}Usage: $0 <backup_file>${NC}"
    exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
    echo -e "${RED}Error: Backup file not found: $BACKUP_FILE${NC}"
    exit 1
fi

echo -e "${RED}WARNING: This will replace all data in database '$DB_NAME'${NC}"
read -p "Are you sure? Type 'yes' to proceed: " -r
echo

if [[ ! $REPLY =~ ^yes$ ]]; then
    echo "Restore cancelled."
    exit 0
fi

echo -e "${YELLOW}Restoring...${NC}"

if ! command -v psql &> /dev/null; then
    echo -e "${RED}Error: psql not found${NC}"
    exit 1
fi

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" < "$BACKUP_FILE" > /dev/null 2>&1 && echo -e "${GREEN}✓ Restore completed!${NC}" || echo -e "${RED}✗ Restore failed!${NC}"