#!/usr/bin/env bash
# Enregistre (ou met à jour) les connecteurs Debezium via l'API REST de Kafka Connect.
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
DIR="$(cd "$(dirname "$0")" && pwd)"

until curl -sf "$CONNECT_URL/connectors" >/dev/null; do
  echo "En attente de Kafka Connect ($CONNECT_URL)..."; sleep 3
done

for f in "$DIR"/*-connector.json; do
  name=$(basename "$f" .json)
  echo "-> $name"
  jq '.config' "$f" | curl -sf -X PUT -H "Content-Type: application/json" \
    --data @- "$CONNECT_URL/connectors/$(jq -r .name "$f")/config" | jq -c '{name, type}'
done
