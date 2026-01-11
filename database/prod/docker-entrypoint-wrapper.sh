#!/bin/sh
set -e

# Copy certs from read-only bind mount into writable volume
mkdir -p /certs
cp -f /certs-src/server.crt /certs/server.crt
cp -f /certs-src/server.key /certs/server.key
cp -f /certs-src/clients_ca.crt /certs/clients_ca.crt
cp -f /certs-src/pgbouncer.crt /certs/pgbouncer.crt
cp -f /certs-src/pgbouncer.key /certs/pgbouncer.key

# Fix perms required by Postgres
chown -R postgres:postgres /certs
chmod 600 /certs/server.key
chmod 644 /certs/server.crt /certs/clients_ca.crt
chmod 644 /certs/pgbouncer.key

# Hand off to the official entrypoint (it will drop privileges properly)
exec /usr/local/bin/docker-entrypoint.sh "$@"