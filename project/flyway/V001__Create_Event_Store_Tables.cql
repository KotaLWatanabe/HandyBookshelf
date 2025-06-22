-- HandyBookshelf Database Migration V001
-- Create Event Store Tables for Event Sourcing
-- Author: HandyBookshelf Team
-- Date: 2025-01-22

-- ========================================
-- Keyspace Creation
-- ========================================

CREATE KEYSPACE IF NOT EXISTS handybookshelf
WITH REPLICATION = {
  'class': 'SimpleStrategy',
  'replication_factor': 1
};

USE handybookshelf;

-- ========================================
-- Event Store Tables
-- ========================================

-- Main event store table for Event Sourcing
-- Stores all domain events from actors (UserSessionActor, BookshelfActor)
CREATE TABLE IF NOT EXISTS event_store (
  persistence_id TEXT,
  sequence_nr BIGINT,
  event_timestamp TIMESTAMP,
  event_type TEXT,
  event_data TEXT,
  metadata TEXT,
  tags SET<TEXT>,
  PRIMARY KEY (persistence_id, sequence_nr)
) WITH CLUSTERING ORDER BY (sequence_nr ASC)
  AND comment = 'Event store for domain events with optimized read pattern';

-- Snapshot store table for Event Sourcing optimization
-- Stores periodic snapshots to optimize replay performance  
CREATE TABLE IF NOT EXISTS snapshot_store (
  persistence_id TEXT,
  sequence_nr BIGINT,
  snapshot_timestamp TIMESTAMP,
  snapshot_data TEXT,
  metadata TEXT,
  PRIMARY KEY (persistence_id, sequence_nr)
) WITH CLUSTERING ORDER BY (sequence_nr DESC)
  AND comment = 'Snapshot store for optimized event replay';

-- Event metadata table for querying and analytics
CREATE TABLE IF NOT EXISTS event_metadata (
  persistence_id TEXT,
  event_type TEXT,
  event_date DATE,
  sequence_nr BIGINT,
  event_timestamp TIMESTAMP,
  user_account_id UUID,
  session_id TEXT,
  PRIMARY KEY ((persistence_id, event_type), event_date, sequence_nr)
) WITH CLUSTERING ORDER BY (event_date DESC, sequence_nr DESC)
  AND comment = 'Event metadata for efficient querying by type and date';

-- ========================================
-- Create Secondary Indexes
-- ========================================

-- Index for querying events by type
CREATE INDEX IF NOT EXISTS idx_event_store_event_type 
ON event_store (event_type);

-- Index for querying events by timestamp
CREATE INDEX IF NOT EXISTS idx_event_store_timestamp 
ON event_store (event_timestamp);

-- Index for querying snapshots by timestamp
CREATE INDEX IF NOT EXISTS idx_snapshot_store_timestamp 
ON snapshot_store (snapshot_timestamp);