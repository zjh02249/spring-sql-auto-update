-- V1.0.1__add_user_index.sql
-- Add index to users table

CREATE INDEX idx_users_email ON users(email);

CREATE INDEX idx_products_name ON products(name);
