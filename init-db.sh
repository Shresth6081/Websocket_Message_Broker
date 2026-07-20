#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE chatdb;
    GRANT ALL PRIVILEGES ON DATABASE chatdb TO chatuser;
EOSQL
echo "Databases initialized."
