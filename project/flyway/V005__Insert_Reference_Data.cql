-- HandyBookshelf Database Migration V005
-- Insert Reference Data and Configuration
-- Author: HandyBookshelf Team
-- Date: 2025-01-22

USE handybookshelf;

-- ========================================
-- Configuration Tables
-- ========================================

-- Application configuration table
CREATE TABLE IF NOT EXISTS app_config (
  config_key TEXT,
  config_value TEXT,
  config_type TEXT,
  description TEXT,
  updated_at TIMESTAMP,
  updated_by TEXT,
  PRIMARY KEY (config_key)
) WITH comment = 'Application configuration settings';

-- Book status reference table
CREATE TABLE IF NOT EXISTS book_status_ref (
  status_code TEXT,
  status_name TEXT,
  status_description TEXT,
  display_order INT,
  is_active BOOLEAN,
  created_at TIMESTAMP,
  PRIMARY KEY (status_code)
) WITH comment = 'Reference data for book status values';

-- Book location reference table
CREATE TABLE IF NOT EXISTS book_location_ref (
  location_code TEXT,
  location_name TEXT,
  location_description TEXT,
  parent_location TEXT,
  display_order INT,
  is_active BOOLEAN,
  created_at TIMESTAMP,
  PRIMARY KEY (location_code)
) WITH comment = 'Reference data for book location values';

-- Event type reference table
CREATE TABLE IF NOT EXISTS event_type_ref (
  event_type TEXT,
  event_category TEXT,
  event_description TEXT,
  is_user_visible BOOLEAN,
  retention_days INT,
  created_at TIMESTAMP,
  PRIMARY KEY (event_type)
) WITH comment = 'Reference data for event types and their properties';

-- ========================================
-- Insert Reference Data
-- ========================================

-- Application Configuration
INSERT INTO app_config (config_key, config_value, config_type, description, updated_at, updated_by)
VALUES ('app.version', '1.0.0', 'STRING', 'Application version', toTimestamp(now()), 'system');

INSERT INTO app_config (config_key, config_value, config_type, description, updated_at, updated_by)
VALUES ('session.timeout.hours', '24', 'INTEGER', 'Default session timeout in hours', toTimestamp(now()), 'system');

INSERT INTO app_config (config_key, config_value, config_type, description, updated_at, updated_by)
VALUES ('session.extension.hours', '2', 'INTEGER', 'Session extension duration in hours', toTimestamp(now()), 'system');

INSERT INTO app_config (config_key, config_value, config_type, description, updated_at, updated_by)
VALUES ('bookshelf.max.books.per.user', '10000', 'INTEGER', 'Maximum books per user bookshelf', toTimestamp(now()), 'system');

INSERT INTO app_config (config_key, config_value, config_type, description, updated_at, updated_by)
VALUES ('search.max.results', '100', 'INTEGER', 'Maximum search results per query', toTimestamp(now()), 'system');

-- Book Status Reference Data
INSERT INTO book_status_ref (status_code, status_name, status_description, display_order, is_active, created_at)
VALUES ('AVAILABLE', 'Available', 'Book is available for reading', 1, true, toTimestamp(now()));

INSERT INTO book_status_ref (status_code, status_name, status_description, display_order, is_active, created_at)
VALUES ('READING', 'Currently Reading', 'Book is currently being read', 2, true, toTimestamp(now()));

INSERT INTO book_status_ref (status_code, status_name, status_description, display_order, is_active, created_at)
VALUES ('READ', 'Read', 'Book has been read', 3, true, toTimestamp(now()));

INSERT INTO book_status_ref (status_code, status_name, status_description, display_order, is_active, created_at)
VALUES ('WANT_TO_READ', 'Want to Read', 'Book is in wish list', 4, true, toTimestamp(now()));

INSERT INTO book_status_ref (status_code, status_name, status_description, display_order, is_active, created_at)
VALUES ('LOANED', 'Loaned Out', 'Book is loaned to someone', 5, true, toTimestamp(now()));

INSERT INTO book_status_ref (status_code, status_name, status_description, display_order, is_active, created_at)
VALUES ('LOST', 'Lost', 'Book is lost or missing', 6, true, toTimestamp(now()));

-- Book Location Reference Data
INSERT INTO book_location_ref (location_code, location_name, location_description, parent_location, display_order, is_active, created_at)
VALUES ('HOME', 'Home', 'Books at home', null, 1, true, toTimestamp(now()));

INSERT INTO book_location_ref (location_code, location_name, location_description, parent_location, display_order, is_active, created_at)
VALUES ('HOME_BEDROOM', 'Bedroom', 'Bedroom bookshelf', 'HOME', 1, true, toTimestamp(now()));

INSERT INTO book_location_ref (location_code, location_name, location_description, parent_location, display_order, is_active, created_at)
VALUES ('HOME_LIVINGROOM', 'Living Room', 'Living room bookshelf', 'HOME', 2, true, toTimestamp(now()));

INSERT INTO book_location_ref (location_code, location_name, location_description, parent_location, display_order, is_active, created_at)
VALUES ('HOME_STUDY', 'Study Room', 'Study room bookshelf', 'HOME', 3, true, toTimestamp(now()));

INSERT INTO book_location_ref (location_code, location_name, location_description, parent_location, display_order, is_active, created_at)
VALUES ('OFFICE', 'Office', 'Books at office', null, 2, true, toTimestamp(now()));

INSERT INTO book_location_ref (location_code, location_name, location_description, parent_location, display_order, is_active, created_at)
VALUES ('DIGITAL', 'Digital', 'Digital/E-books', null, 3, true, toTimestamp(now()));

INSERT INTO book_location_ref (location_code, location_name, location_description, parent_location, display_order, is_active, created_at)
VALUES ('STORAGE', 'Storage', 'Books in storage', null, 4, true, toTimestamp(now()));

-- Event Type Reference Data
INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('UserLoggedIn', 'AUTHENTICATION', 'User logged into the system', true, 90, toTimestamp(now()));

INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('UserLoggedOut', 'AUTHENTICATION', 'User logged out from the system', true, 90, toTimestamp(now()));

INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('SessionExtended', 'AUTHENTICATION', 'User session was extended', false, 30, toTimestamp(now()));

INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('SessionExpired', 'AUTHENTICATION', 'User session expired', false, 30, toTimestamp(now()));

INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('BookAddedToShelf', 'BOOKSHELF', 'Book was added to bookshelf', true, 365, toTimestamp(now()));

INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('BookRemovedFromShelf', 'BOOKSHELF', 'Book was removed from bookshelf', true, 365, toTimestamp(now()));

INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('SorterChanged', 'BOOKSHELF', 'Bookshelf sorting was changed', false, 30, toTimestamp(now()));

INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('FilterAddedToBook', 'BOOKSHELF', 'Filter was added to a book', true, 180, toTimestamp(now()));

INSERT INTO event_type_ref (event_type, event_category, event_description, is_user_visible, retention_days, created_at)
VALUES ('FilterRemovedFromBook', 'BOOKSHELF', 'Filter was removed from a book', true, 180, toTimestamp(now()));

-- ========================================
-- Create Indexes for Reference Tables
-- ========================================

CREATE INDEX IF NOT EXISTS idx_book_status_ref_active 
ON book_status_ref (is_active);

CREATE INDEX IF NOT EXISTS idx_book_location_ref_active 
ON book_location_ref (is_active);

CREATE INDEX IF NOT EXISTS idx_book_location_ref_parent 
ON book_location_ref (parent_location);

CREATE INDEX IF NOT EXISTS idx_event_type_ref_category 
ON event_type_ref (event_category);

CREATE INDEX IF NOT EXISTS idx_event_type_ref_user_visible 
ON event_type_ref (is_user_visible);