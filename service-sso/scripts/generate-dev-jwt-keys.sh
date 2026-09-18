#!/bin/sh
# Génère la paire de clés RSA de DÉVELOPPEMENT pour la signature des JWT.
# Ces clés sont committées volontairement (projet école) — ne jamais faire ça en prod.
set -eu
KEYS_DIR="$(dirname "$0")/../src/main/resources/keys"
mkdir -p "$KEYS_DIR"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS_DIR/dev-private.pem"
openssl pkey -in "$KEYS_DIR/dev-private.pem" -pubout -out "$KEYS_DIR/dev-public.pem"
echo "Clés générées dans $KEYS_DIR"
