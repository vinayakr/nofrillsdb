#!/usr/bin/env bash
set -euo pipefail

mkdir -p certs
cd certs

# ---- 1) Client CA (used to sign customer certs) ----
openssl genrsa -out clients_ca.key 4096
openssl req -x509 -new -nodes -key clients_ca.key -sha256 -days 3650 \
  -subj "/C=US/ST=WA/O=NoFrillsDB/CN=NoFrillsDB Client CA" \
  -out clients_ca.crt

# ---- 2) Server key + CSR ----
openssl genrsa -out server.key 2048

# IMPORTANT: change CN/SAN to match how clients will reach it (hostname)
DB_HOSTNAME="localhost"

cat > server.ext <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:${DB_HOSTNAME},IP:127.0.0.1
EOF

openssl req -new -key server.key \
  -subj "/C=US/ST=WA/O=NoFrillsDB/CN=${DB_HOSTNAME}" \
  -out server.csr

# Server cert signed by the same CA for simplicity in the lab
openssl x509 -req -in server.csr -CA clients_ca.crt -CAkey clients_ca.key -CAcreateserial \
  -out server.crt -days 825 -sha256 -extfile server.ext

# ---- 3) Customer client cert (CN MUST equal db username) ----
CUSTOMER_USER="cust_demo"

openssl genrsa -out ${CUSTOMER_USER}.key 2048

cat > client.ext <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature
extendedKeyUsage=clientAuth
EOF

openssl req -new -key ${CUSTOMER_USER}.key \
  -subj "/C=US/ST=WA/O=Customer/CN=${CUSTOMER_USER}" \
  -out ${CUSTOMER_USER}.csr

openssl x509 -req -in ${CUSTOMER_USER}.csr -CA clients_ca.crt -CAkey clients_ca.key -CAcreateserial \
  -out ${CUSTOMER_USER}.crt -days 825 -sha256 -extfile client.ext

# Clean up CSRs/ext
rm -f *.csr *.ext

echo "Generated:"
ls -1