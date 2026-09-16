FROM postgres:16-alpine

# Keep repo folders tidy while still running SQL in a deterministic order.
COPY db/tables/ /docker-entrypoint-initdb.d/tables/
COPY db/data/ /docker-entrypoint-initdb.d/data/
COPY db/docker-entrypoint-init.sh /docker-entrypoint-initdb.d/00-run-sql.sh
RUN chmod +x /docker-entrypoint-initdb.d/00-run-sql.sh

# IP restrictions
ENV POSTGRES_PASSWORD=${DB_PASSWORD:-change_me}

RUN printf '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"\n' > /docker-entrypoint-initdb.d/01-restrict-ip.sh && \
    chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

EXPOSE 5432