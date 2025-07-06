-- HandyBookshelf Database Migration V004
-- Create Materialized Views for Optimized Queries
-- Author: HandyBookshelf Team
-- Date: 2025-01-22

USE handybookshelf;

-- ========================================
-- Materialized Views for Query Optimization
-- ========================================

-- Books ordered by addition date (most recent first)
CREATE MATERIALIZED VIEW IF NOT EXISTS books_by_recent_addition AS
  SELECT user_account_id, book_id, book_title, book_isbn, book_authors, book_tags, 
         book_location, book_status, added_at, updated_at
  FROM bookshelf_read_model
  WHERE user_account_id IS NOT NULL 
    AND book_id IS NOT NULL 
    AND added_at IS NOT NULL
  PRIMARY KEY (user_account_id, added_at, book_id)
  WITH CLUSTERING ORDER BY (added_at DESC, book_id ASC)
  AND comment = 'Books ordered by recent addition for timeline views';

-- Books ordered by last update (most recently updated first)
CREATE MATERIALIZED VIEW IF NOT EXISTS books_by_recent_update AS
  SELECT user_account_id, book_id, book_title, book_isbn, book_authors, book_tags,
         book_location, book_status, added_at, updated_at
  FROM bookshelf_read_model
  WHERE user_account_id IS NOT NULL 
    AND book_id IS NOT NULL 
    AND updated_at IS NOT NULL
  PRIMARY KEY (user_account_id, updated_at, book_id)
  WITH CLUSTERING ORDER BY (updated_at DESC, book_id ASC)
  AND comment = 'Books ordered by recent updates for activity tracking';

-- Books by status with recent activity
CREATE MATERIALIZED VIEW IF NOT EXISTS active_books_by_status AS
  SELECT user_account_id, book_status, book_id, book_title, book_location, 
         added_at, updated_at
  FROM bookshelf_read_model
  WHERE user_account_id IS NOT NULL 
    AND book_status IS NOT NULL 
    AND book_id IS NOT NULL
    AND updated_at IS NOT NULL
  PRIMARY KEY (user_account_id, book_status, updated_at, book_id)
  WITH CLUSTERING ORDER BY (book_status ASC, updated_at DESC, book_id ASC)
  AND comment = 'Books grouped by status with recent activity first';

-- User session activity summary
CREATE MATERIALIZED VIEW IF NOT EXISTS recent_user_activity AS
  SELECT user_account_id, activity_timestamp, activity_type, session_id, 
         activity_data, ip_address
  FROM user_activity_log
  WHERE user_account_id IS NOT NULL 
    AND activity_timestamp IS NOT NULL
    AND activity_type IS NOT NULL
  PRIMARY KEY (user_account_id, activity_type, activity_timestamp)
  WITH CLUSTERING ORDER BY (activity_type ASC, activity_timestamp DESC)
  AND comment = 'Recent user activity grouped by type for analytics';

-- Active sessions summary
CREATE MATERIALIZED VIEW IF NOT EXISTS current_active_sessions AS
  SELECT user_account_id, session_id, created_at, last_activity, expires_at,
         ip_address, user_agent
  FROM user_sessions
  WHERE user_account_id IS NOT NULL 
    AND session_id IS NOT NULL
    AND is_active IS NOT NULL
    AND is_active = true
    AND last_activity IS NOT NULL
  PRIMARY KEY (user_account_id, last_activity, session_id)
  WITH CLUSTERING ORDER BY (last_activity DESC, session_id ASC)
  AND comment = 'Currently active sessions ordered by last activity';

-- Event summary by type for analytics
CREATE MATERIALIZED VIEW IF NOT EXISTS events_by_type_recent AS
  SELECT persistence_id, event_type, event_timestamp, sequence_nr, metadata
  FROM event_store
  WHERE persistence_id IS NOT NULL 
    AND sequence_nr IS NOT NULL
    AND event_type IS NOT NULL
    AND event_timestamp IS NOT NULL
  PRIMARY KEY (event_type, event_timestamp, persistence_id, sequence_nr)
  WITH CLUSTERING ORDER BY (event_timestamp DESC, persistence_id ASC, sequence_nr ASC)
  AND comment = 'Events grouped by type with recent events first for analytics';

-- ========================================
-- User-Defined Functions (UDFs) for Common Operations
-- ========================================

-- Function to extract title prefix for search optimization
CREATE OR REPLACE FUNCTION title_prefix(title TEXT)
  CALLED ON NULL INPUT
  RETURNS TEXT
  LANGUAGE java AS 
  'return title != null && title.length() > 0 ? title.substring(0, Math.min(3, title.length())).toLowerCase() : "";';

-- Function to calculate days since addition
CREATE OR REPLACE FUNCTION days_since_added(added_at TIMESTAMP)
  CALLED ON NULL INPUT
  RETURNS INT
  LANGUAGE java AS 
  'long diff = System.currentTimeMillis() - added_at.getTime();
   return (int)(diff / (24 * 60 * 60 * 1000));';

-- Function to format book display name
CREATE OR REPLACE FUNCTION book_display_name(title TEXT, authors SET<TEXT>)
  CALLED ON NULL INPUT
  RETURNS TEXT
  LANGUAGE java AS 
  'if (title == null) return "";
   if (authors == null || authors.isEmpty()) return title;
   return title + " by " + String.join(", ", authors);';