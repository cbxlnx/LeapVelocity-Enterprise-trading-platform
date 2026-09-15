FROM postgres:16-alpine

# Keep repo folders tidy while still running SQL in a deterministic order.
COPY db/tables/ /docker-entrypoint-initdb.d/tables/
COPY db/data/ /docker-entrypoint-initdb.d/data/
COPY db/docker-entrypoint-init.sh /docker-entrypoint-initdb.d/00-run-sql.sh
RUN chmod +x /docker-entrypoint-initdb.d/00-run-sql.sh

EXPOSE 5432
