FROM postgres:15

ENV POSTGRES_PASSWORD=${DB_PASSWORD:-n3u3d4!}

RUN echo "host all all 10.9.0.0/16 scram-sha-256" >> /usr/share/postgresql/5432/pg_hba.conf.sample \
    && echo "host all all 10.9.0.0/16 scram-sha-256" >> /var/lib/postgresql/data/pg_hba.conf || true

RUN echo '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"' > /docker-entrypoint-initdb.d/01-restrict-ip.sh \
    && chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

# Copy reorganized SQL schema and seed files
COPY database/sql/schemas /docker-entrypoint-initdb.d/schemas
COPY database/sql/seeds /docker-entrypoint-initdb.d/seeds

VOLUME /var/lib/postgresql/data

EXPOSE 5432