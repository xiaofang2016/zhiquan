CREATE DATABASE IF NOT EXISTS sport_crawl DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sport_crawl;

CREATE TABLE IF NOT EXISTS sport_league (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_id INT NOT NULL,
  sport_type INT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  payload LONGTEXT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_type (source_id, sport_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sport_match_date (
  match_date DATE NOT NULL,
  total_match INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (match_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sport_match (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  cid INT NULL,
  sport_type INT NULL,
  list_date DATE NOT NULL,
  match_time DATETIME NULL,
  is_hot TINYINT NOT NULL DEFAULT 0,
  list_section VARCHAR(16) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  payload LONGTEXT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_source (source_id),
  KEY idx_list (list_date, list_section, sort_order),
  KEY idx_cid (list_date, cid),
  KEY idx_type (list_date, sport_type),
  KEY idx_hot (list_date, is_hot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sport_image (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_url VARCHAR(2048) NOT NULL,
  source_hash CHAR(64) NOT NULL,
  content_type VARCHAR(64) NULL,
  byte_size INT NOT NULL DEFAULT 0,
  relative_path VARCHAR(255) NULL,
  status VARCHAR(16) NOT NULL,
  error_message VARCHAR(255) NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hash (source_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
