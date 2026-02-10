-- V1.0.1__add_indexes.sql
-- Add indexes to improve query performance

CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name);

CREATE INDEX IF NOT EXISTS idx_customers_created_at ON customers(created_at);

CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name);
