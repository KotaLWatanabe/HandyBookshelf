#!/bin/bash
# HandyBookshelf Services Startup Script

set -e

echo "🚀 Starting HandyBookshelf services..."

# Create scripts directory if it doesn't exist
mkdir -p scripts

# Check if Docker and Docker Compose are available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Set Docker Compose command (try new 'docker compose' first, fallback to 'docker-compose')
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

echo "📋 Using Docker Compose: $DOCKER_COMPOSE"

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
            $DOCKER_COMPOSE exec -T scylladb cqlsh -u cassandra -p cassandra -e "DESCRIBE KEYSPACES;" > /dev/null 2>&1
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
$DOCKER_COMPOSE up -d scylladb elasticsearch redis

# Wait for ScyllaDB
wait_for_service "ScyllaDB" 9042

# Wait for Elasticsearch  
wait_for_service "Elasticsearch" 9200

echo "ℹ️  Note: HandyBookshelf application is not started in Docker."
echo "   To run the application locally, use: sbt \"project controller\" run"

# Start optional services
if [ "$2" == "--with-kibana" ] || [ "$1" == "--with-kibana" ]; then
    echo "📊 Starting Kibana..."
    $DOCKER_COMPOSE up -d kibana
    wait_for_service "Kibana" 5601
fi

echo ""
echo "🎉 All infrastructure services are up and running!"
echo ""
echo "📍 Service URLs:"
echo "   🗄️  ScyllaDB CQL:       localhost:9042"
echo "   🔍 Elasticsearch:      http://localhost:9200" 
echo "   💾 Redis:              localhost:6379"

if $DOCKER_COMPOSE ps kibana &> /dev/null; then
    echo "   📊 Kibana:             http://localhost:5601"
fi

echo ""
echo "🔧 Management commands:"
echo "   View logs:    $DOCKER_COMPOSE logs -f [service_name]"
echo "   Stop all:     $DOCKER_COMPOSE down"
echo "   Stop & clean: $DOCKER_COMPOSE down -v"
echo ""
echo "🚀 To run HandyBookshelf application locally:"
echo "   sbt \"project controller\" run"
echo "   # App will be available at: http://localhost:8080"
echo ""

# Show service status
echo "📊 Service Status:"
$DOCKER_COMPOSE ps

# Optional: Show initial logs
if [ "$3" == "--logs" ] || [ "$2" == "--logs" ] || [ "$1" == "--logs" ]; then
    echo ""
    echo "📋 Recent logs:"
    $DOCKER_COMPOSE logs --tail=20
fi