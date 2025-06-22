#!/bin/bash
# Wait for services to be ready

set -e

echo "Waiting for ScyllaDB to be ready..."
until nc -z scylladb 9042; do
  echo "ScyllaDB is unavailable - sleeping"
  sleep 2
done
echo "ScyllaDB is ready!"

echo "Waiting for Elasticsearch to be ready..."
until curl -f http://elasticsearch:9200/_cluster/health?wait_for_status=yellow&timeout=30s; do
  echo "Elasticsearch is unavailable - sleeping"
  sleep 2
done
echo "Elasticsearch is ready!"

echo "All services are ready. Starting application..."
exec "$@"