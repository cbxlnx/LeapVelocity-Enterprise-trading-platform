FROM postgres:15

ENV POSTGRES_PASSWORD=my_secure_password

RUN echo "host all all 10.9.0.0/16 scram-sha-256" >> /usr/share/postgresql/5432/pg_hba.conf.sample \
    && echo "host all all 10.9.0.0/16 scram-sha-256" >> /var/lib/postgresql/data/pg_hba.conf || true

RUN echo '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"' > /docker-entrypoint-initdb.d/01-restrict-ip.sh \
    && chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

ARG SCHEMA_PATH=enterprise-schema.sql

COPY database/sql/${SCHEMA_PATH} /docker-entrypoint-initdb.d/02-myschema.sql
COPY database/sql/seed-data.sql /docker-entrypoint-initdb.d/03-seed-data.sql

VOLUME /var/lib/postgresql/data

EXPOSE 5432
