-- HandyBookshelf ScyllaDB Initialization Script
-- Create keyspace for HandyBookshelf application

CREATE KEYSPACE IF NOT EXISTS handybookshelf
WITH REPLICATION = {
  'class': 'SimpleStrategy',
  'replication_factor': 1
};

USE handybookshelf;

-- Event Store table for Event Sourcing
-- Stores all domain events from UserSessionActor and BookshelfActor
CREATE TABLE IF NOT EXISTS event_store (
  persistence_id TEXT,
  sequence_nr BIGINT,
  event_timestamp TIMESTAMP,
  event_type TEXT,
  event_data TEXT,
  metadata TEXT,
  PRIMARY KEY (persistence_id, sequence_nr)
) WITH CLUSTERING ORDER BY (sequence_nr ASC);

-- Snapshot store table for Event Sourcing
-- Stores periodic snapshots to optimize replay performance  
CREATE TABLE IF NOT EXISTS snapshot_store (
  persistence_id TEXT,
  sequence_nr BIGINT,
  snapshot_timestamp TIMESTAMP,
  snapshot_data TEXT,
  metadata TEXT,
  PRIMARY KEY (persistence_id, sequence_nr)
) WITH CLUSTERING ORDER BY (sequence_nr DESC);

-- User session management table
-- Fast lookup for session validation
CREATE TABLE IF NOT EXISTS user_sessions (
  user_account_id UUID,
  session_id TEXT,
  created_at TIMESTAMP,
  last_activity TIMESTAMP,
  expires_at TIMESTAMP,
  is_active BOOLEAN,
  metadata TEXT,
  PRIMARY KEY (user_account_id, session_id)
) WITH CLUSTERING ORDER BY (session_id ASC);

-- Bookshelf read model table (Query side for CQRS)
-- Optimized for read operations
CREATE TABLE IF NOT EXISTS bookshelf_read_model (
  user_account_id UUID,
  book_id UUID,
  book_title TEXT,
  book_isbn TEXT,
  book_authors SET<TEXT>,
  book_tags SET<TEXT>,
  added_at TIMESTAMP,
  updated_at TIMESTAMP,
  book_location TEXT,
  book_status TEXT,
  metadata TEXT,
  PRIMARY KEY (user_account_id, book_id)
) WITH CLUSTERING ORDER BY (book_id ASC);

-- Book lookup by ISBN (secondary index)
CREATE TABLE IF NOT EXISTS books_by_isbn (
  isbn TEXT,
  book_id UUID,
  user_account_id UUID,
  book_title TEXT,
  added_at TIMESTAMP,
  PRIMARY KEY (isbn, book_id, user_account_id)
) WITH CLUSTERING ORDER BY (book_id ASC, user_account_id ASC);

-- User activity log
CREATE TABLE IF NOT EXISTS user_activity_log (
  user_account_id UUID,
  activity_timestamp TIMESTAMP,
  activity_type TEXT,
  activity_data TEXT,
  session_id TEXT,
  PRIMARY KEY (user_account_id, activity_timestamp)
) WITH CLUSTERING ORDER BY (activity_timestamp DESC);

-- Create secondary indexes for common queries
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at 
ON user_sessions (expires_at);

CREATE INDEX IF NOT EXISTS idx_sessions_is_active 
ON user_sessions (is_active);

CREATE INDEX IF NOT EXISTS idx_bookshelf_title 
ON bookshelf_read_model (book_title);

CREATE INDEX IF NOT EXISTS idx_bookshelf_added_at 
ON bookshelf_read_model (added_at);

-- Insert initial test data (optional)
-- INSERT INTO user_sessions (user_account_id, session_id, created_at, last_activity, expires_at, is_active, metadata)
-- VALUES (uuid(), 'test-session-1', toTimestamp(now()), toTimestamp(now()), toTimestamp(now()) + 86400000, true, '{}');