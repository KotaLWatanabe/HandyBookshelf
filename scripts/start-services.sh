#!/bin/bash
# HandyBookshelf Services Startup Script

set -e

echo "🚀 Starting HandyBookshelf services..."

# Create scripts directory if it doesn't exist
mkdir -p scripts

# Check if Podman and Podman Compose are available
if ! command -v podman &> /dev/null; then
    echo "❌ Podman is not installed. Please install Podman first."
    exit 1
fi

if ! command -v podman-compose &> /dev/null && ! podman compose version &> /dev/null; then
    echo "❌ Podman Compose is not installed. Please install podman-compose first."
    exit 1
fi

# Set Podman Compose command (try 'podman compose' first, fallback to 'podman-compose')
if podman compose version &> /dev/null; then
    PODMAN_COMPOSE="podman compose"
else
    PODMAN_COMPOSE="podman-compose"
fi

echo "📋 Using Podman Compose: $PODMAN_COMPOSE"

# Function to wait for service
wait_for_service() {
    local service_name=$1
    local port=$2
    local max_attempts=30
    local attempt=1

    echo "⏳ Waiting for $service_name to be ready on port $port..."
    
    while [ $attempt -le $max_attempts ]; do
        if nc -z localhost $port 2>/dev/null; then
            echo "✅ $service_name is ready!"
            return 0
        fi
        
        echo "   Attempt $attempt/$max_attempts: $service_name not ready yet..."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo "❌ $service_name failed to start within expected time"
    return 1
}

# Function to check service health
check_service_health() {
    local service_name=$1
    echo "🔍 Checking $service_name health..."
    
    case $service_name in
        "scylladb")
            $PODMAN_COMPOSE exec -T scylladb cqlsh -u cassandra -p cassandra -e "DESCRIBE KEYSPACES;" > /dev/null 2>&1
            ;;
        "elasticsearch")
            curl -f http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=30s > /dev/null 2>&1
            ;;
        # "handybookshelf-app")
        #     curl -f http://localhost:8080/health > /dev/null 2>&1 || true
        #     ;;
        *)
            echo "Unknown service: $service_name"
            return 1
            ;;
    esac
}

# Start infrastructure services only
echo "🏗️  Starting infrastructure services..."
$PODMAN_COMPOSE up -d scylladb elasticsearch redis

# Wait for ScyllaDB
wait_for_service "ScyllaDB" 9042

# Wait for Elasticsearch  
wait_for_service "Elasticsearch" 9200

echo "ℹ️  Note: HandyBookshelf application is not started in Podman."
echo "   To run the application locally, use: sbt \"project controller\" run"

# Start optional services
if [ "$2" == "--with-kibana" ] || [ "$1" == "--with-kibana" ]; then
    echo "📊 Starting Kibana..."
    $PODMAN_COMPOSE up -d kibana
    wait_for_service "Kibana" 5601
fi

echo ""
echo "🎉 All infrastructure services are up and running!"
echo ""
echo "📍 Service URLs:"
echo "   🗄️  ScyllaDB CQL:       localhost:9042"
echo "   🔍 Elasticsearch:      http://localhost:9200" 
echo "   💾 Redis:              localhost:6379"

if $PODMAN_COMPOSE ps kibana &> /dev/null; then
    echo "   📊 Kibana:             http://localhost:5601"
fi

echo ""
echo "🔧 Management commands:"
echo "   View logs:    $PODMAN_COMPOSE logs -f [service_name]"
echo "   Stop all:     $PODMAN_COMPOSE down"
echo "   Stop & clean: $PODMAN_COMPOSE down -v"
echo ""
echo "🚀 To run HandyBookshelf application locally:"
echo "   sbt \"project controller\" run"
echo "   # App will be available at: http://localhost:8080"
echo ""

# Show service status
echo "📊 Service Status:"
$PODMAN_COMPOSE ps

# Optional: Show initial logs
if [ "$3" == "--logs" ] || [ "$2" == "--logs" ] || [ "$1" == "--logs" ]; then
    echo ""
    echo "📋 Recent logs:"
    $PODMAN_COMPOSE logs --tail=20
fi