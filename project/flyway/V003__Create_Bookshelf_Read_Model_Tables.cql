-- HandyBookshelf Database Migration V003
-- Create Bookshelf Read Model Tables (CQRS Query Side)
-- Author: HandyBookshelf Team
-- Date: 2025-01-22

USE handybookshelf;

-- ========================================
-- Bookshelf Read Model Tables (CQRS Query Side)
-- ========================================

-- Main bookshelf read model table
-- Optimized for read operations and queries
CREATE TABLE IF NOT EXISTS bookshelf_read_model (
  user_account_id UUID,
  book_id UUID,
  book_title TEXT,
  book_isbn TEXT,
  book_authors SET<TEXT>,
  book_tags SET<TEXT>,
  book_location TEXT,
  book_status TEXT,
  added_at TIMESTAMP,
  updated_at TIMESTAMP,
  metadata MAP<TEXT, TEXT>,
  PRIMARY KEY (user_account_id, book_id)
) WITH CLUSTERING ORDER BY (book_id ASC)
  AND comment = 'Bookshelf read model optimized for user queries';

-- Books organized by title for search
CREATE TABLE IF NOT EXISTS books_by_title (
  title_prefix TEXT,
  book_title TEXT,
  user_account_id UUID,
  book_id UUID,
  book_isbn TEXT,
  book_authors SET<TEXT>,
  added_at TIMESTAMP,
  PRIMARY KEY (title_prefix, book_title, user_account_id, book_id)
) WITH CLUSTERING ORDER BY (book_title ASC, user_account_id ASC, book_id ASC)
  AND comment = 'Books organized by title for efficient title-based search';

-- Books organized by ISBN for lookup
CREATE TABLE IF NOT EXISTS books_by_isbn (
  isbn TEXT,
  book_id UUID,
  user_account_id UUID,
  book_title TEXT,
  book_authors SET<TEXT>,
  added_at TIMESTAMP,
  PRIMARY KEY (isbn, book_id, user_account_id)
) WITH CLUSTERING ORDER BY (book_id ASC, user_account_id ASC)
  AND comment = 'Books organized by ISBN for efficient ISBN-based lookup';

-- Books organized by tags for filtering
CREATE TABLE IF NOT EXISTS books_by_tag (
  tag TEXT,
  user_account_id UUID,
  book_id UUID,
  book_title TEXT,
  book_isbn TEXT,
  added_at TIMESTAMP,
  PRIMARY KEY (tag, user_account_id, book_id)
) WITH CLUSTERING ORDER BY (user_account_id ASC, book_id ASC)
  AND comment = 'Books organized by tags for efficient tag-based filtering';

-- Books organized by status for management
CREATE TABLE IF NOT EXISTS books_by_status (
  user_account_id UUID,
  book_status TEXT,
  updated_at TIMESTAMP,
  book_id UUID,
  book_title TEXT,
  book_location TEXT,
  PRIMARY KEY (user_account_id, book_status, updated_at, book_id)
) WITH CLUSTERING ORDER BY (book_status ASC, updated_at DESC, book_id ASC)
  AND comment = 'Books organized by status for management queries';

-- Books organized by location for physical management
CREATE TABLE IF NOT EXISTS books_by_location (
  user_account_id UUID,
  book_location TEXT,
  book_id UUID,
  book_title TEXT,
  book_isbn TEXT,
  added_at TIMESTAMP,
  PRIMARY KEY (user_account_id, book_location, book_id)
) WITH CLUSTERING ORDER BY (book_location ASC, book_id ASC)
  AND comment = 'Books organized by location for physical management';

-- Recent books activity for dashboard
CREATE TABLE IF NOT EXISTS recent_books_activity (
  user_account_id UUID,
  activity_date DATE,
  activity_timestamp TIMESTAMP,
  activity_type TEXT,
  book_id UUID,
  book_title TEXT,
  PRIMARY KEY (user_account_id, activity_date, activity_timestamp, book_id)
) WITH CLUSTERING ORDER BY (activity_date DESC, activity_timestamp DESC, book_id ASC)
  AND comment = 'Recent book activities for dashboard and timeline';

-- ========================================
-- Create Secondary Indexes
-- ========================================

-- Index for searching books by title
CREATE INDEX IF NOT EXISTS idx_bookshelf_title 
ON bookshelf_read_model (book_title);

-- Index for searching books by status
CREATE INDEX IF NOT EXISTS idx_bookshelf_status 
ON bookshelf_read_model (book_status);

-- Index for searching books by location
CREATE INDEX IF NOT EXISTS idx_bookshelf_location 
ON bookshelf_read_model (book_location);

-- Index for sorting books by added date
CREATE INDEX IF NOT EXISTS idx_bookshelf_added_at 
ON bookshelf_read_model (added_at);

-- Index for sorting books by updated date
CREATE INDEX IF NOT EXISTS idx_bookshelf_updated_at 
ON bookshelf_read_model (updated_at);