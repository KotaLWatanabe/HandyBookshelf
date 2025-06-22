#!/bin/bash
# HandyBookshelf Local Development Runner

set -e

echo "🚀 Starting HandyBookshelf application locally..."

# Check if infrastructure services are running
echo "🔍 Checking infrastructure services..."

# Check ScyllaDB
if ! nc -z localhost 9042 2>/dev/null; then
    echo "❌ ScyllaDB is not running on localhost:9042"
    echo "   Please start infrastructure services first:"
    echo "   ./scripts/start-services.sh"
    exit 1
fi

# Check Elasticsearch
if ! curl -f http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=5s > /dev/null 2>&1; then
    echo "❌ Elasticsearch is not running on localhost:9200"
    echo "   Please start infrastructure services first:"
    echo "   ./scripts/start-services.sh"
    exit 1
fi

echo "✅ Infrastructure services are running"

# Load environment variables
if [ -f .env.local ]; then
    echo "📄 Loading local environment variables..."
    export $(grep -v '^#' .env.local | xargs)
fi

# Start the application
echo "🎯 Starting HandyBookshelf application..."
echo "   📍 Application will be available at: http://localhost:8080"
echo ""

# Run with sbt
sbt "project controller" run