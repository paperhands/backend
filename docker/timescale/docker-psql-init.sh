#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE sentimental_dev;
    GRANT ALL PRIVILEGES ON DATABASE sentimental_dev TO root;
EOSQL
