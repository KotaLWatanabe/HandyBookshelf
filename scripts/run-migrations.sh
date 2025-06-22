#!/bin/bash
# HandyBookshelf Database Migration Runner
# Executes ScyllaDB migrations in correct order

set -e

echo "🗄️  HandyBookshelf Database Migration Runner"
echo "============================================="

# Configuration
SCYLLA_HOST=${SCYLLA_HOST:-localhost}
SCYLLA_PORT=${SCYLLA_PORT:-9042}
SCYLLA_USERNAME=${SCYLLA_USERNAME:-cassandra}
SCYLLA_PASSWORD=${SCYLLA_PASSWORD:-cassandra}
SCYLLA_KEYSPACE=${SCYLLA_KEYSPACE:-handybookshelf}

# Migration files directory
MIGRATION_DIR="project/flyway"
TARGET_VERSION=${1:-"all"}

# Check if ScyllaDB is running
echo "🔍 Checking ScyllaDB connection..."
if ! nc -z $SCYLLA_HOST $SCYLLA_PORT; then
    echo "❌ ScyllaDB is not running on $SCYLLA_HOST:$SCYLLA_PORT"
    echo "   Please start ScyllaDB first:"
    echo "   docker-compose up -d scylladb"
    exit 1
fi

echo "✅ ScyllaDB is running"

# Function to execute CQL file
execute_migration() {
    local file=$1
    local version=$(basename "$file" .cql)
    
    echo "📄 Executing migration: $version"
    
    # Use docker-compose exec if ScyllaDB is running in Docker
    if docker-compose ps scylladb | grep -q "Up"; then
        docker-compose exec -T scylladb cqlsh -u $SCYLLA_USERNAME -p $SCYLLA_PASSWORD -f "/docker-entrypoint-initdb.d/$(basename $file)"
    else
        # Use local cqlsh if available
        if command -v cqlsh &> /dev/null; then
            cqlsh -u $SCYLLA_USERNAME -p $SCYLLA_PASSWORD -f "$file" $SCYLLA_HOST $SCYLLA_PORT
        else
            echo "❌ cqlsh not found. Please install Cassandra tools or use Docker."
            exit 1
        fi
    fi
    
    if [ $? -eq 0 ]; then
        echo "✅ Migration $version completed successfully"
    else
        echo "❌ Migration $version failed"
        exit 1
    fi
}

# Function to check if migration directory exists
check_migration_dir() {
    if [ ! -d "$MIGRATION_DIR" ]; then
        echo "❌ Migration directory not found: $MIGRATION_DIR"
        echo "   Please run this script from the project root directory"
        exit 1
    fi
}

# Function to copy migration files to Docker container
copy_migrations_to_docker() {
    if docker-compose ps scylladb | grep -q "Up"; then
        echo "📋 Copying migration files to ScyllaDB container..."
        for file in $MIGRATION_DIR/V*.cql; do
            if [ -f "$file" ]; then
                docker cp "$file" "$(docker-compose ps -q scylladb):/docker-entrypoint-initdb.d/"
            fi
        done
        echo "✅ Migration files copied"
    fi
}

# Main execution
main() {
    check_migration_dir
    copy_migrations_to_docker
    
    echo ""
    echo "🚀 Starting database migrations..."
    echo "   Target: $TARGET_VERSION"
    echo "   Host: $SCYLLA_HOST:$SCYLLA_PORT"
    echo "   Keyspace: $SCYLLA_KEYSPACE"
    echo ""
    
    # Get list of migration files in order
    migration_files=($(ls $MIGRATION_DIR/V*.cql | sort -V))
    
    if [ ${#migration_files[@]} -eq 0 ]; then
        echo "❌ No migration files found in $MIGRATION_DIR"
        exit 1
    fi
    
    # Execute migrations
    for file in "${migration_files[@]}"; do
        version=$(basename "$file" .cql | cut -d'_' -f1)
        
        # Check if we should stop at target version
        if [ "$TARGET_VERSION" != "all" ] && [ "$version" \> "$TARGET_VERSION" ]; then
            echo "🛑 Stopping at target version: $TARGET_VERSION"
            break
        fi
        
        execute_migration "$file"
        echo ""
    done
    
    echo "🎉 All migrations completed successfully!"
    echo ""
    
    # Show final status
    echo "📊 Database Status:"
    if docker-compose ps scylladb | grep -q "Up"; then
        docker-compose exec -T scylladb cqlsh -u $SCYLLA_USERNAME -p $SCYLLA_PASSWORD -e "USE $SCYLLA_KEYSPACE; DESCRIBE TABLES;"
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [TARGET_VERSION]"
    echo ""
    echo "Examples:"
    echo "  $0           # Run all migrations"
    echo "  $0 V003      # Run migrations up to V003"
    echo "  $0 all       # Run all migrations (same as no argument)"
    echo ""
    echo "Available migrations:"
    if [ -d "$MIGRATION_DIR" ]; then
        ls $MIGRATION_DIR/V*.cql | while read file; do
            version=$(basename "$file" .cql)
            description=$(head -n 3 "$file" | grep -o "-- .*" | tail -n 1 | sed 's/-- //')
            echo "  $version: $description"
        done
    fi
}

# Handle command line arguments
case "${1:-}" in
    -h|--help)
        show_usage
        exit 0
        ;;
    *)
        main
        ;;
esac