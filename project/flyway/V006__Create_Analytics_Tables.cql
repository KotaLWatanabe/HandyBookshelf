-- HandyBookshelf Database Migration V006
-- Create Analytics and Reporting Tables
-- Author: HandyBookshelf Team
-- Date: 2025-01-22

USE handybookshelf;

-- ========================================
-- Analytics and Reporting Tables
-- ========================================

-- Daily usage statistics
CREATE TABLE IF NOT EXISTS daily_stats (
  stat_date DATE,
  metric_type TEXT,
  metric_value BIGINT,
  details MAP<TEXT, TEXT>,
  calculated_at TIMESTAMP,
  PRIMARY KEY (stat_date, metric_type)
) WITH comment = 'Daily aggregated statistics for analytics';

-- User engagement metrics
CREATE TABLE IF NOT EXISTS user_engagement_metrics (
  user_account_id UUID,
  metric_date DATE,
  books_added INT,
  books_removed INT,
  sessions_count INT,
  total_session_duration_minutes INT,
  last_activity TIMESTAMP,
  PRIMARY KEY (user_account_id, metric_date)
) WITH CLUSTERING ORDER BY (metric_date DESC)
  AND comment = 'User engagement metrics by date';

-- Popular books across all users
CREATE TABLE IF NOT EXISTS popular_books (
  time_period TEXT, -- 'daily', 'weekly', 'monthly'
  period_start DATE,
  rank INT,
  book_isbn TEXT,
  book_title TEXT,
  book_authors SET<TEXT>,
  user_count INT,
  add_count INT,
  last_updated TIMESTAMP,
  PRIMARY KEY (time_period, period_start, rank)
) WITH CLUSTERING ORDER BY (period_start DESC, rank ASC)
  AND comment = 'Popular books ranking by time period';

-- Search analytics
CREATE TABLE IF NOT EXISTS search_analytics (
  search_date DATE,
  search_hour INT,
  search_term TEXT,
  user_account_id UUID,
  results_count INT,
  clicked_result_rank INT,
  search_timestamp TIMESTAMP,
  PRIMARY KEY (search_date, search_hour, search_timestamp, user_account_id)
) WITH CLUSTERING ORDER BY (search_hour ASC, search_timestamp DESC, user_account_id ASC)
  AND comment = 'Search analytics for improving search functionality';

-- System performance metrics
CREATE TABLE IF NOT EXISTS system_performance_metrics (
  metric_timestamp TIMESTAMP,
  metric_name TEXT,
  metric_value DOUBLE,
  metric_unit TEXT,
  host_name TEXT,
  service_name TEXT,
  PRIMARY KEY (metric_name, metric_timestamp)
) WITH CLUSTERING ORDER BY (metric_timestamp DESC)
  AND comment = 'System performance metrics for monitoring';

-- Error logs and monitoring
CREATE TABLE IF NOT EXISTS error_logs (
  error_timestamp TIMESTAMP,
  error_level TEXT,
  error_message TEXT,
  error_details TEXT,
  stack_trace TEXT,
  user_account_id UUID,
  session_id TEXT,
  request_id TEXT,
  service_name TEXT,
  PRIMARY KEY (error_level, error_timestamp)
) WITH CLUSTERING ORDER BY (error_timestamp DESC)
  AND comment = 'Error logs for debugging and monitoring';

-- Feature usage tracking
CREATE TABLE IF NOT EXISTS feature_usage (
  feature_name TEXT,
  usage_date DATE,
  user_account_id UUID,
  usage_count INT,
  last_used TIMESTAMP,
  PRIMARY KEY (feature_name, usage_date, user_account_id)
) WITH CLUSTERING ORDER BY (usage_date DESC, user_account_id ASC)
  AND comment = 'Feature usage tracking for product insights';

-- Data retention policy tracking
CREATE TABLE IF NOT EXISTS data_retention_log (
  table_name TEXT,
  cleanup_date DATE,
  cleanup_timestamp TIMESTAMP,
  records_deleted BIGINT,
  cleanup_duration_ms BIGINT,
  cleanup_criteria TEXT,
  PRIMARY KEY (table_name, cleanup_date, cleanup_timestamp)
) WITH CLUSTERING ORDER BY (cleanup_date DESC, cleanup_timestamp DESC)
  AND comment = 'Data retention and cleanup tracking';

-- ========================================
-- Aggregation Tables for Fast Queries
-- ========================================

-- Monthly user statistics
CREATE TABLE IF NOT EXISTS monthly_user_stats (
  user_account_id UUID,
  year_month TEXT, -- Format: "2025-01"
  total_books INT,
  books_added_this_month INT,
  books_removed_this_month INT,
  total_sessions INT,
  total_session_time_minutes INT,
  most_active_day DATE,
  calculated_at TIMESTAMP,
  PRIMARY KEY (user_account_id, year_month)
) WITH CLUSTERING ORDER BY (year_month DESC)
  AND comment = 'Monthly aggregated user statistics';

-- System-wide monthly statistics
CREATE TABLE IF NOT EXISTS monthly_system_stats (
  year_month TEXT, -- Format: "2025-01"
  total_users INT,
  active_users INT,
  new_users INT,
  total_books BIGINT,
  total_sessions BIGINT,
  avg_books_per_user DOUBLE,
  avg_session_duration_minutes DOUBLE,
  calculated_at TIMESTAMP,
  PRIMARY KEY (year_month)
) WITH comment = 'Monthly system-wide statistics';

-- ========================================
-- Create Indexes for Analytics Tables
-- ========================================

-- Indexes for daily stats
CREATE INDEX IF NOT EXISTS idx_daily_stats_metric_type 
ON daily_stats (metric_type);

-- Indexes for user engagement
CREATE INDEX IF NOT EXISTS idx_user_engagement_last_activity 
ON user_engagement_metrics (last_activity);

-- Indexes for popular books
CREATE INDEX IF NOT EXISTS idx_popular_books_isbn 
ON popular_books (book_isbn);

-- Indexes for search analytics
CREATE INDEX IF NOT EXISTS idx_search_analytics_term 
ON search_analytics (search_term);

CREATE INDEX IF NOT EXISTS idx_search_analytics_user 
ON search_analytics (user_account_id);

-- Indexes for error logs
CREATE INDEX IF NOT EXISTS idx_error_logs_user 
ON error_logs (user_account_id);

CREATE INDEX IF NOT EXISTS idx_error_logs_service 
ON error_logs (service_name);

-- Indexes for feature usage
CREATE INDEX IF NOT EXISTS idx_feature_usage_user 
ON feature_usage (user_account_id);

-- ========================================
-- Sample Analytics Queries (as comments for reference)
-- ========================================

/*
-- Top 10 most added books this month
SELECT book_isbn, book_title, book_authors, add_count 
FROM popular_books 
WHERE time_period = 'monthly' 
  AND period_start = '2025-01-01' 
ORDER BY rank ASC 
LIMIT 10;

-- Active users in the last 7 days
SELECT COUNT(DISTINCT user_account_id) as active_users
FROM user_engagement_metrics 
WHERE metric_date >= dateOf(now()) - 7
  AND (books_added > 0 OR books_removed > 0 OR sessions_count > 0);

-- Most searched terms today
SELECT search_term, COUNT(*) as search_count
FROM search_analytics 
WHERE search_date = dateOf(now())
GROUP BY search_term 
ORDER BY search_count DESC 
LIMIT 20;

-- Users with most books
SELECT user_account_id, total_books
FROM monthly_user_stats 
WHERE year_month = '2025-01'
ORDER BY total_books DESC 
LIMIT 10;

-- System performance over time
SELECT metric_timestamp, metric_value
FROM system_performance_metrics 
WHERE metric_name = 'response_time_ms'
  AND metric_timestamp >= dateOf(now()) - 1
ORDER BY metric_timestamp DESC;
*/