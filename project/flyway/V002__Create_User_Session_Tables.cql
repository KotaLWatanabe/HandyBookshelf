-- HandyBookshelf Database Migration V002
-- Create User Session Management Tables
-- Author: HandyBookshelf Team
-- Date: 2025-01-22

USE handybookshelf;

-- ========================================
-- User Session Management Tables
-- ========================================

-- Primary user sessions table
-- Fast lookup for session validation and management
CREATE TABLE IF NOT EXISTS user_sessions (
  user_account_id UUID,
  session_id TEXT,
  created_at TIMESTAMP,
  last_activity TIMESTAMP,
  expires_at TIMESTAMP,
  is_active BOOLEAN,
  ip_address INET,
  user_agent TEXT,
  metadata MAP<TEXT, TEXT>,
  PRIMARY KEY (user_account_id, session_id)
) WITH CLUSTERING ORDER BY (session_id ASC)
  AND comment = 'User session management with fast lookup by user';

-- Sessions by expiration for cleanup
CREATE TABLE IF NOT EXISTS sessions_by_expiration (
  expiration_bucket DATE,
  expires_at TIMESTAMP,
  user_account_id UUID,
  session_id TEXT,
  PRIMARY KEY (expiration_bucket, expires_at, user_account_id, session_id)
) WITH CLUSTERING ORDER BY (expires_at ASC, user_account_id ASC, session_id ASC)
  AND comment = 'Sessions organized by expiration for efficient cleanup';

-- Active sessions lookup
CREATE TABLE IF NOT EXISTS active_sessions (
  is_active BOOLEAN,
  last_activity TIMESTAMP,
  user_account_id UUID,
  session_id TEXT,
  expires_at TIMESTAMP,
  PRIMARY KEY (is_active, last_activity, user_account_id, session_id)
) WITH CLUSTERING ORDER BY (last_activity DESC, user_account_id ASC, session_id ASC)
  AND comment = 'Active sessions for monitoring and management';

-- User activity log for audit trail
CREATE TABLE IF NOT EXISTS user_activity_log (
  user_account_id UUID,
  activity_timestamp TIMESTAMP,
  activity_type TEXT,
  activity_data TEXT,
  session_id TEXT,
  ip_address INET,
  user_agent TEXT,
  PRIMARY KEY (user_account_id, activity_timestamp)
) WITH CLUSTERING ORDER BY (activity_timestamp DESC)
  AND comment = 'User activity audit log';

-- ========================================
-- Create Secondary Indexes
-- ========================================

-- Index for querying sessions by expiration time
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at 
ON user_sessions (expires_at);

-- Index for querying sessions by active status
CREATE INDEX IF NOT EXISTS idx_sessions_is_active 
ON user_sessions (is_active);

-- Index for querying sessions by last activity
CREATE INDEX IF NOT EXISTS idx_sessions_last_activity 
ON user_sessions (last_activity);

-- Index for querying activity by type
CREATE INDEX IF NOT EXISTS idx_activity_log_type 
ON user_activity_log (activity_type);