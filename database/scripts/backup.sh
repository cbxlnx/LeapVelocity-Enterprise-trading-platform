#!/bin/bash
# Backup Database - Creates timestamped SQL dump

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-leapvelocity_trading_db}"
DB_USER="${DB_USER:-trading_user}"

if [ -f "$(dirname "$0")/../.env" ]; then
    source "$(dirname "$0")/../.env"
fi

YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

BACKUP_DIR="$(dirname "$0")/backups"
mkdir -p "$BACKUP_DIR"

if [ -z "$1" ]; then
    TIMESTAMP=$(date +%Y-%m-%d_%H-%M-%S)
    BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql"
else
    BACKUP_FILE="$BACKUP_DIR/$1.sql"
fi

echo -e "${YELLOW}Backing up database: $DB_NAME${NC}"
echo "Output: $BACKUP_FILE"

if ! command -v pg_dump &> /dev/null; then
    echo -e "${RED}Error: pg_dump not found${NC}"
    exit 1
fi

if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" > "$BACKUP_FILE" 2>&1; then
    SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
    echo -e "${GREEN}✓ Backup completed! Size: $SIZE${NC}"
else
    echo -e "${RED}✗ Backup failed!${NC}"
    rm -f "$BACKUP_FILE"
    exit 1
fi