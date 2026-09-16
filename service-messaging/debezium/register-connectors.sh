#!/bin/sh
# Enregistre (idempotent, PUT) les connecteurs Debezium outbox auprès de Kafka
# Connect. Lancé par le service compose kafka-connect-init à chaque
# `docker compose up`, même esprit que le provisioning automatique de Grafana.
set -eu

CONNECT_URL="http://kafka-connect:8083"

echo "Attente de l'API Kafka Connect ($CONNECT_URL)..."
until curl -sf "$CONNECT_URL/connectors" >/dev/null; do
  sleep 2
done

for config in /connectors/*.json; do
  name=$(basename "$config" .json)
  echo "Enregistrement du connecteur $name..."
  curl -sf -X PUT -H "Content-Type: application/json" \
    --data @"$config" \
    "$CONNECT_URL/connectors/$name/config" \
    -o /dev/null
done

echo "Connecteurs enregistrés."
